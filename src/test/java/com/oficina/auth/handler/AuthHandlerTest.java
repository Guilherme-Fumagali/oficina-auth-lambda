package com.oficina.auth.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.auth.banco.ConsultaException;
import com.oficina.auth.cliente.Cliente;
import com.oficina.auth.cliente.ClienteRepository;
import com.oficina.auth.cliente.StatusCliente;
import com.oficina.auth.dto.ErroResponse;
import com.oficina.auth.funcionario.Funcionario;
import com.oficina.auth.funcionario.FuncionarioRepository;
import com.oficina.auth.token.TokenIssuer;
import com.oficina.auth.token.TokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthHandlerTest {

    private static final String SEGREDO = "chave-de-teste-super-longa-para-hmac-sha-256-minimo-32-chars";
    private static final String CPF_VALIDO = "529.982.247-25";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock ClienteRepository repositorio;
    @Mock FuncionarioRepository funcionarios;

    private AuthHandler handler;
    private ContextoFake contexto;

    @BeforeEach
    void setUp() {
        handler = new AuthHandler(repositorio, funcionarios, new TokenIssuer(SEGREDO));
        contexto = new ContextoFake();
    }

    @Test
    void clienteAtivoRecebeTokenValido() throws Exception {
        var id = UUID.randomUUID();
        when(repositorio.buscarPorCpf(any()))
            .thenReturn(Optional.of(new Cliente(id, "João", StatusCliente.ATIVO)));

        var resposta = handler.handleRequest(evento(CPF_VALIDO), contexto);

        assertThat(resposta.getStatusCode()).isEqualTo(200);
        var corpo = JSON.readTree(resposta.getBody());
        assertThat(corpo.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(corpo.get("expiresIn").asLong()).isEqualTo(900);

        var claims = new TokenValidator(SEGREDO).validar(corpo.get("accessToken").asText()).orElseThrow();
        assertThat(claims.getSubject()).isEqualTo(id.toString());
    }

    @Test
    void cpfMalformadoDevolve400SemConsultarOBanco() throws Exception {
        var resposta = handler.handleRequest(evento("123.456.789-00"), contexto);

        assertThat(resposta.getStatusCode()).isEqualTo(400);
        assertThat(JSON.readTree(resposta.getBody()).get("erro").asText()).isEqualTo("CPF_INVALIDO");
        verify(repositorio, never()).buscarPorCpf(any());
    }

    @Test
    void corpoAusenteDevolve400() {
        var vazio = APIGatewayV2HTTPEvent.builder().withBody(null).build();
        assertThat(handler.handleRequest(vazio, contexto).getStatusCode()).isEqualTo(400);
    }

    @Test
    void clienteInexistenteDevolve401Generico() throws Exception {
        when(repositorio.buscarPorCpf(any())).thenReturn(Optional.empty());

        var resposta = handler.handleRequest(evento(CPF_VALIDO), contexto);

        assertThat(resposta.getStatusCode()).isEqualTo(401);
        var corpo = JSON.readTree(resposta.getBody());
        assertThat(corpo.get("erro").asText()).isEqualTo("AUTENTICACAO_RECUSADA");
        assertThat(corpo.get("mensagem").asText()).isEqualTo(ErroResponse.FALHA_GENERICA);
        assertThat(String.join("\n", contexto.linhas)).contains("resultado=nao_encontrado");
    }

    @ParameterizedTest
    @EnumSource(value = StatusCliente.class, names = "ATIVO", mode = Mode.EXCLUDE)
    void clienteForaDeAtivoDevolve401Generico(StatusCliente status) throws Exception {
        when(repositorio.buscarPorCpf(any()))
            .thenReturn(Optional.of(new Cliente(UUID.randomUUID(), "João", status)));

        var resposta = handler.handleRequest(evento(CPF_VALIDO), contexto);

        assertThat(resposta.getStatusCode()).isEqualTo(401);
        assertThat(JSON.readTree(resposta.getBody()).get("erro").asText()).isEqualTo("AUTENTICACAO_RECUSADA");
        assertThat(String.join("\n", contexto.linhas)).contains("resultado=sem_permissao");
    }

    @Test
    void cpfSemCadastroECadastroBloqueadoTemRespostaIdentica() throws Exception {
        when(repositorio.buscarPorCpf(any())).thenReturn(Optional.empty());
        var naoEncontrado = handler.handleRequest(evento(CPF_VALIDO), contexto);

        when(repositorio.buscarPorCpf(any()))
            .thenReturn(Optional.of(new Cliente(UUID.randomUUID(), "João", StatusCliente.BLOQUEADO)));
        var bloqueado = handler.handleRequest(evento(CPF_VALIDO), contexto);

        assertThat(bloqueado.getStatusCode()).isEqualTo(naoEncontrado.getStatusCode());
        assertThat(bloqueado.getHeaders()).isEqualTo(naoEncontrado.getHeaders());
        assertThat(bloqueado.getBody()).isEqualTo(naoEncontrado.getBody());
    }

    @Test
    void falhaDeBancoDevolve500SemVazarDetalhe() throws Exception {
        when(repositorio.buscarPorCpf(any()))
            .thenThrow(new ConsultaException("conexão recusada", null));

        var resposta = handler.handleRequest(evento(CPF_VALIDO), contexto);

        assertThat(resposta.getStatusCode()).isEqualTo(500);
        assertThat(resposta.getBody()).doesNotContain("conexão recusada");
    }

    @Test
    void logNuncaDeveConterOCpfCompleto() {
        when(repositorio.buscarPorCpf(any()))
            .thenReturn(Optional.of(new Cliente(UUID.randomUUID(), "João", StatusCliente.ATIVO)));

        handler.handleRequest(evento(CPF_VALIDO), contexto);

        assertThat(contexto.linhas).isNotEmpty();
        assertThat(String.join("\n", contexto.linhas))
            .doesNotContain("52998224725")
            .contains("***725");
    }

    @Test
    void clienteRecebeTokenComPapelCliente() throws Exception {
        when(repositorio.buscarPorCpf(any()))
            .thenReturn(Optional.of(new Cliente(UUID.randomUUID(), "João", StatusCliente.ATIVO)));

        var resposta = handler.handleRequest(evento(CPF_VALIDO), contexto);

        var token = JSON.readTree(resposta.getBody()).get("accessToken").asText();
        assertThat(new TokenValidator(SEGREDO).validar(token).orElseThrow().get("role")).isEqualTo("CLIENTE");
        verify(funcionarios, never()).buscarPorCpf(any());
    }

    @Test
    void funcionarioAtivoRecebeTokenComPapelFuncionario() throws Exception {
        var id = UUID.randomUUID();
        when(funcionarios.buscarPorCpf(any())).thenReturn(Optional.of(new Funcionario(id, "Maria", true)));

        var resposta = handler.handleRequest(eventoFuncionario(CPF_VALIDO), contexto);

        assertThat(resposta.getStatusCode()).isEqualTo(200);
        var token = JSON.readTree(resposta.getBody()).get("accessToken").asText();
        var claims = new TokenValidator(SEGREDO).validar(token).orElseThrow();
        assertThat(claims.getSubject()).isEqualTo(id.toString());
        assertThat(claims.get("role")).isEqualTo("FUNCIONARIO");
        verify(repositorio, never()).buscarPorCpf(any());
    }

    @Test
    void funcionarioInexistenteEInativoTemRespostaIdentica() throws Exception {
        when(funcionarios.buscarPorCpf(any())).thenReturn(Optional.empty());
        var naoEncontrado = handler.handleRequest(eventoFuncionario(CPF_VALIDO), contexto);

        when(funcionarios.buscarPorCpf(any()))
            .thenReturn(Optional.of(new Funcionario(UUID.randomUUID(), "Maria", false)));
        var inativo = handler.handleRequest(eventoFuncionario(CPF_VALIDO), contexto);

        assertThat(naoEncontrado.getStatusCode()).isEqualTo(401);
        assertThat(inativo.getStatusCode()).isEqualTo(401);
        assertThat(inativo.getBody()).isEqualTo(naoEncontrado.getBody());
    }

    private APIGatewayV2HTTPEvent evento(String cpf) {
        return APIGatewayV2HTTPEvent.builder()
            .withRouteKey("POST /auth")
            .withBody("{\"cpf\":\"" + cpf + "\"}")
            .build();
    }

    private APIGatewayV2HTTPEvent eventoFuncionario(String cpf) {
        return APIGatewayV2HTTPEvent.builder()
            .withRouteKey(AuthHandler.ROTA_FUNCIONARIOS)
            .withBody("{\"cpf\":\"" + cpf + "\"}")
            .build();
    }

    private static class ContextoFake implements Context {
        final List<String> linhas = new ArrayList<>();

        @Override public LambdaLogger getLogger() {
            return new LambdaLogger() {
                @Override public void log(String message) { linhas.add(message); }
                @Override public void log(byte[] message) { linhas.add(new String(message)); }
            };
        }
        @Override public String getAwsRequestId() { return "test"; }
        @Override public String getLogGroupName() { return "test"; }
        @Override public String getLogStreamName() { return "test"; }
        @Override public String getFunctionName() { return "oficina-auth"; }
        @Override public String getFunctionVersion() { return "1"; }
        @Override public String getInvokedFunctionArn() { return "arn:test"; }
        @Override public com.amazonaws.services.lambda.runtime.CognitoIdentity getIdentity() { return null; }
        @Override public com.amazonaws.services.lambda.runtime.ClientContext getClientContext() { return null; }
        @Override public int getRemainingTimeInMillis() { return 10_000; }
        @Override public int getMemoryLimitInMB() { return 512; }
    }
}
