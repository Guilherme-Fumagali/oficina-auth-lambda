package com.oficina.auth.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.auth.cliente.Cliente;
import com.oficina.auth.cliente.ClienteRepository;
import com.oficina.auth.cliente.StatusCliente;
import com.oficina.auth.dto.ErroResponse;
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

    private AuthHandler handler;
    private ContextoFake contexto;

    @BeforeEach
    void setUp() {
        handler = new AuthHandler(repositorio, new TokenIssuer(SEGREDO));
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
    void clienteInexistenteDevolve404() throws Exception {
        when(repositorio.buscarPorCpf(any())).thenReturn(Optional.empty());

        var resposta = handler.handleRequest(evento(CPF_VALIDO), contexto);

        assertThat(resposta.getStatusCode()).isEqualTo(404);
        assertThat(JSON.readTree(resposta.getBody()).get("erro").asText())
            .isEqualTo("CLIENTE_NAO_ENCONTRADO");
    }

    @ParameterizedTest
    @EnumSource(value = StatusCliente.class, names = "ATIVO", mode = Mode.EXCLUDE)
    void clienteForaDeAtivoDevolve403(StatusCliente status) throws Exception {
        when(repositorio.buscarPorCpf(any()))
            .thenReturn(Optional.of(new Cliente(UUID.randomUUID(), "João", status)));

        var resposta = handler.handleRequest(evento(CPF_VALIDO), contexto);

        assertThat(resposta.getStatusCode()).isEqualTo(403);
        assertThat(JSON.readTree(resposta.getBody()).get("erro").asText()).isEqualTo("CLIENTE_INATIVO");
    }

    @Test
    void mensagemDe404EDe403DevemSerIndistinguiveis() throws Exception {
        when(repositorio.buscarPorCpf(any())).thenReturn(Optional.empty());
        var naoEncontrado = JSON.readTree(handler.handleRequest(evento(CPF_VALIDO), contexto).getBody());

        when(repositorio.buscarPorCpf(any()))
            .thenReturn(Optional.of(new Cliente(UUID.randomUUID(), "João", StatusCliente.BLOQUEADO)));
        var bloqueado = JSON.readTree(handler.handleRequest(evento(CPF_VALIDO), contexto).getBody());

        // Enumeração de CPFs válidos fica impossível pelo corpo da resposta (SPEC-01 §8).
        assertThat(naoEncontrado.get("mensagem").asText())
            .isEqualTo(bloqueado.get("mensagem").asText())
            .isEqualTo(ErroResponse.FALHA_GENERICA);
    }

    @Test
    void falhaDeBancoDevolve500SemVazarDetalhe() throws Exception {
        when(repositorio.buscarPorCpf(any()))
            .thenThrow(new ClienteRepository.ConsultaClienteException("conexão recusada", null));

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

    private APIGatewayV2HTTPEvent evento(String cpf) {
        return APIGatewayV2HTTPEvent.builder()
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
