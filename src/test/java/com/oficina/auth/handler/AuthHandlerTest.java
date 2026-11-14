package com.oficina.auth.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.auth.cliente.Cliente;
import com.oficina.auth.cliente.StatusCliente;
import com.oficina.auth.cpf.Cpf;
import com.oficina.auth.desafio.GeradorCodigo;
import com.oficina.auth.desafio.HashCodigo;
import com.oficina.auth.dto.DesafioResponse;
import com.oficina.auth.dto.ErroResponse;
import com.oficina.auth.funcionario.Funcionario;
import com.oficina.auth.identidade.Cadastro;
import com.oficina.auth.identidade.Papel;
import com.oficina.auth.token.TokenIssuer;
import com.oficina.auth.token.TokenValidator;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.oficina.auth.handler.AuthHandler.ROTA_CLIENTES;
import static com.oficina.auth.handler.AuthHandler.ROTA_CONFIRMACAO_CLIENTES;
import static com.oficina.auth.handler.AuthHandler.ROTA_CONFIRMACAO_FUNCIONARIOS;
import static com.oficina.auth.handler.AuthHandler.ROTA_FUNCIONARIOS;
import static org.assertj.core.api.Assertions.assertThat;

class AuthHandlerTest {

    private static final String SEGREDO_JWT = "chave-de-teste-super-longa-para-hmac-sha-256-minimo-32-chars";
    private static final String SEGREDO_OTP = "segredo-otp-de-teste-com-pelo-menos-32-bytes";
    private static final Instant INICIO = Instant.parse("2026-10-29T12:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Cpf CLIENTE_ATIVO = Cpf.de("529.982.247-25");
    private static final Cpf CLIENTE_BLOQUEADO = Cpf.de("111.444.777-35");
    private static final Cpf CLIENTE_SEM_EMAIL = Cpf.de("153.509.460-56");
    private static final Cpf SEM_CADASTRO = Cpf.de("390.533.447-05");
    private static final Cpf FUNCIONARIO_ATIVO = Cpf.de("390.533.447-05");
    private static final Cpf FUNCIONARIO_INATIVO = Cpf.de("153.509.460-56");

    private static final UUID ID_CLIENTE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ID_FUNCIONARIO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Cliente JOAO = new Cliente(ID_CLIENTE, "João da Silva", StatusCliente.ATIVO);

    private final IdentidadesFake identidades = new IdentidadesFake()
        .com(Papel.CLIENTE, CLIENTE_ATIVO, new Cadastro(JOAO, "joao@example.com"))
        .com(Papel.CLIENTE, CLIENTE_BLOQUEADO, new Cadastro(
            new Cliente(UUID.randomUUID(), "Ana", StatusCliente.BLOQUEADO), "ana@example.com"))
        .com(Papel.CLIENTE, CLIENTE_SEM_EMAIL, new Cadastro(
            new Cliente(UUID.randomUUID(), "Carlos", StatusCliente.ATIVO), null))
        .com(Papel.FUNCIONARIO, FUNCIONARIO_ATIVO, new Cadastro(
            new Funcionario(ID_FUNCIONARIO, "Maria Souza", true), "maria@example.com"))
        .com(Papel.FUNCIONARIO, FUNCIONARIO_INATIVO, new Cadastro(
            new Funcionario(UUID.randomUUID(), "Pedro", false), "pedro@example.com"));
    private final DesafiosEmMemoria desafios = new DesafiosEmMemoria();
    private final EnviadorFake enviador = new EnviadorFake();
    private final RelogioAjustavel relogio = new RelogioAjustavel(INICIO);
    private final HashCodigo hash = new HashCodigo(SEGREDO_OTP);
    private final ContextoFake contexto = new ContextoFake();
    private final AuthHandler handler = new AuthHandler(identidades, desafios, enviador,
        new GeradorCodigo(), hash, new TokenIssuer(SEGREDO_JWT), relogio);

    @Test
    void clienteAtivoRecebeDesafioEOCodigoNoEmailCadastrado() throws Exception {
        var resposta = iniciar(ROTA_CLIENTES, CLIENTE_ATIVO);

        assertThat(resposta.getStatusCode()).isEqualTo(202);
        var corpo = corpo(resposta);
        var desafioId = corpo.get("desafioId").asText();
        assertThat(UUID.fromString(desafioId)).isNotNull();
        assertThat(corpo.get("mensagem").asText()).isEqualTo(DesafioResponse.MENSAGEM);
        assertThat(corpo.get("expiraEmSegundos").asLong()).isEqualTo(300);

        assertThat(enviador.enviados).singleElement().satisfies(envio -> {
            assertThat(envio.destinatario()).isEqualTo("joao@example.com");
            assertThat(envio.nome()).isEqualTo("João da Silva");
            assertThat(envio.codigo()).matches("\\d{6}");
        });
        assertThat(contexto.log()).contains("resultado=codigo_enviado");
    }

    @Test
    void desafioGuardaSoOHashDoCodigoComValidadeDeCincoMinutos() throws Exception {
        var desafioId = desafioId(iniciar(ROTA_CLIENTES, CLIENTE_ATIVO));
        var codigo = enviador.ultimoCodigo();

        var desafio = desafios.buscar(desafioId).orElseThrow();

        assertThat(desafio.codigoHash()).isEqualTo(hash.calcular(desafioId, codigo)).doesNotContain(codigo);
        assertThat(desafio.titular()).isEqualTo(JOAO);
        assertThat(desafio.cpf()).isEqualTo(CLIENTE_ATIVO);
        assertThat(desafio.tentativas()).isZero();
        assertThat(desafio.expiraEm()).isEqualTo(INICIO.plusSeconds(300));
    }

    @Test
    void respostaDeInicioEIdenticaParaCadastroAtivoDesconhecidoEInativo() throws Exception {
        var respostas = List.of(
            iniciar(ROTA_CLIENTES, CLIENTE_ATIVO),
            iniciar(ROTA_CLIENTES, SEM_CADASTRO),
            iniciar(ROTA_CLIENTES, CLIENTE_BLOQUEADO),
            iniciar(ROTA_CLIENTES, CLIENTE_SEM_EMAIL));

        var ids = new HashSet<String>();
        for (var resposta : respostas) {
            assertThat(resposta.getStatusCode()).isEqualTo(202);
            assertThat(resposta.getHeaders()).isEqualTo(respostas.get(0).getHeaders());
            var corpo = corpo(resposta);
            var campos = new ArrayList<String>();
            corpo.fieldNames().forEachRemaining(campos::add);
            assertThat(campos).containsExactlyInAnyOrder("desafioId", "mensagem", "expiraEmSegundos");
            assertThat(corpo.get("mensagem").asText()).isEqualTo(DesafioResponse.MENSAGEM);
            assertThat(corpo.get("expiraEmSegundos").asLong()).isEqualTo(300);
            ids.add(UUID.fromString(corpo.get("desafioId").asText()).toString());
        }
        assertThat(ids).hasSize(4);
        assertThat(desafios.total()).isEqualTo(1);
        assertThat(enviador.enviados).hasSize(1);
    }

    @Test
    void cpfSemCadastroNaoGravaNemEnvia() {
        var resposta = iniciar(ROTA_CLIENTES, SEM_CADASTRO);

        assertThat(resposta.getStatusCode()).isEqualTo(202);
        assertThat(desafios.total()).isZero();
        assertThat(enviador.enviados).isEmpty();
        assertThat(contexto.log()).contains("resultado=nao_encontrado");
    }

    @Test
    void cadastroBloqueadoNaoGravaNemEnvia() {
        var resposta = iniciar(ROTA_CLIENTES, CLIENTE_BLOQUEADO);

        assertThat(resposta.getStatusCode()).isEqualTo(202);
        assertThat(desafios.total()).isZero();
        assertThat(enviador.enviados).isEmpty();
        assertThat(contexto.log()).contains("resultado=sem_permissao");
    }

    @Test
    void cadastroAtivoSemEmailNaoGravaNemEnvia() {
        var resposta = iniciar(ROTA_CLIENTES, CLIENTE_SEM_EMAIL);

        assertThat(resposta.getStatusCode()).isEqualTo(202);
        assertThat(desafios.total()).isZero();
        assertThat(enviador.enviados).isEmpty();
        assertThat(contexto.log()).contains("resultado=sem_email");
    }

    @Test
    void cpfMalformadoDevolve400SemConsultarOOsService() throws Exception {
        var resposta = handler.handleRequest(evento(ROTA_CLIENTES, "{\"cpf\":\"123.456.789-00\"}"), contexto);

        assertThat(resposta.getStatusCode()).isEqualTo(400);
        assertThat(corpo(resposta).get("erro").asText()).isEqualTo("CPF_INVALIDO");
        assertThat(identidades.consultas).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "{", "[]", "{}"})
    void corpoDeInicioAusenteOuInvalidoDevolve400(String corpo) throws Exception {
        var resposta = handler.handleRequest(evento(ROTA_CLIENTES, corpo), contexto);

        assertThat(resposta.getStatusCode()).isEqualTo(400);
        assertThat(corpo(resposta).get("erro").asText()).isEqualTo("CPF_INVALIDO");
        assertThat(identidades.consultas).isEmpty();
    }

    @Test
    void osServiceIndisponivelDevolve503SemGravarNemEnviar() throws Exception {
        identidades.ficarIndisponivel();

        var resposta = iniciar(ROTA_CLIENTES, CLIENTE_ATIVO);

        assertThat(resposta.getStatusCode()).isEqualTo(503);
        assertThat(corpo(resposta).get("erro").asText()).isEqualTo("SERVICO_INDISPONIVEL");
        assertThat(desafios.total()).isZero();
        assertThat(enviador.enviados).isEmpty();
        assertThat(contexto.log()).contains("resultado=os_indisponivel");
    }

    @Test
    void falhaNoEnvioDoEmailApagaODesafioEDevolve503() throws Exception {
        enviador.falhar();

        var resposta = iniciar(ROTA_CLIENTES, CLIENTE_ATIVO);

        assertThat(resposta.getStatusCode()).isEqualTo(503);
        assertThat(corpo(resposta).get("erro").asText()).isEqualTo("SERVICO_INDISPONIVEL");
        assertThat(corpo(resposta).has("desafioId")).isFalse();
        assertThat(desafios.total()).isZero();
        assertThat(contexto.log()).contains("resultado=envio_falhou");
    }

    @Test
    void falhaInesperadaDevolve500SemVazarDetalhe() {
        desafios.falharCom(new IllegalStateException("detalhe interno"));

        var inicio = iniciar(ROTA_CLIENTES, CLIENTE_ATIVO);
        var confirmacao = confirmar(ROTA_CONFIRMACAO_CLIENTES, UUID.randomUUID().toString(), "123456");

        assertThat(inicio.getStatusCode()).isEqualTo(500);
        assertThat(confirmacao.getStatusCode()).isEqualTo(500);
        assertThat(inicio.getBody()).doesNotContain("detalhe interno");
        assertThat(confirmacao.getBody()).doesNotContain("detalhe interno").doesNotContain("accessToken");
    }

    @Test
    void codigoCorretoEmiteTokenDoClienteEApagaODesafio() throws Exception {
        var desafioId = desafioId(iniciar(ROTA_CLIENTES, CLIENTE_ATIVO));

        var resposta = confirmar(ROTA_CONFIRMACAO_CLIENTES, desafioId, enviador.ultimoCodigo());

        assertThat(resposta.getStatusCode()).isEqualTo(200);
        var corpo = corpo(resposta);
        assertThat(corpo.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(corpo.get("expiresIn").asLong()).isEqualTo(900);
        var claims = claims(resposta);
        assertThat(claims.getSubject()).isEqualTo(ID_CLIENTE.toString());
        assertThat(claims.get("role")).isEqualTo("CLIENTE");
        assertThat(claims.get("cpf")).isEqualTo("52998224725");
        assertThat(claims.get("nome")).isEqualTo("João da Silva");
        assertThat(desafios.buscar(desafioId)).isEmpty();
        assertThat(contexto.log()).contains("resultado=autenticado");
    }

    @Test
    void codigoSoPodeSerUsadoUmaVez() {
        var desafioId = desafioId(iniciar(ROTA_CLIENTES, CLIENTE_ATIVO));
        var codigo = enviador.ultimoCodigo();

        assertThat(confirmar(ROTA_CONFIRMACAO_CLIENTES, desafioId, codigo).getStatusCode()).isEqualTo(200);
        assertThat(confirmar(ROTA_CONFIRMACAO_CLIENTES, desafioId, codigo).getStatusCode()).isEqualTo(401);
    }

    @Test
    void confirmacaoConcorrenteEmiteUmUnicoToken() {
        var desafioId = desafioId(iniciar(ROTA_CLIENTES, CLIENTE_ATIVO));
        desafios.outraRequisicaoRemoveAntes();

        var resposta = confirmar(ROTA_CONFIRMACAO_CLIENTES, desafioId, enviador.ultimoCodigo());

        assertThat(resposta.getStatusCode()).isEqualTo(401);
        assertThat(contexto.log()).contains("resultado=desafio_ja_utilizado");
    }

    @Test
    void codigoErradoDevolve401EConsomeUmaTentativa() throws Exception {
        var desafioId = desafioId(iniciar(ROTA_CLIENTES, CLIENTE_ATIVO));

        var resposta = confirmar(ROTA_CONFIRMACAO_CLIENTES, desafioId, codigoErrado());

        assertThat(resposta.getStatusCode()).isEqualTo(401);
        assertThat(corpo(resposta).get("erro").asText()).isEqualTo("AUTENTICACAO_RECUSADA");
        assertThat(corpo(resposta).get("mensagem").asText()).isEqualTo(ErroResponse.FALHA_CONFIRMACAO);
        assertThat(desafios.buscar(desafioId).orElseThrow().tentativas()).isEqualTo(1);
        assertThat(contexto.log()).contains("resultado=codigo_incorreto");
    }

    @Test
    void codigoCertoNaTerceiraTentativaAindaAutentica() {
        var desafioId = desafioId(iniciar(ROTA_CLIENTES, CLIENTE_ATIVO));

        confirmar(ROTA_CONFIRMACAO_CLIENTES, desafioId, codigoErrado());
        confirmar(ROTA_CONFIRMACAO_CLIENTES, desafioId, codigoErrado());

        assertThat(confirmar(ROTA_CONFIRMACAO_CLIENTES, desafioId, enviador.ultimoCodigo()).getStatusCode())
            .isEqualTo(200);
    }

    @Test
    void terceiraTentativaErradaApagaODesafio() {
        var desafioId = desafioId(iniciar(ROTA_CLIENTES, CLIENTE_ATIVO));

        for (int i = 0; i < 3; i++) {
            assertThat(confirmar(ROTA_CONFIRMACAO_CLIENTES, desafioId, codigoErrado()).getStatusCode())
                .isEqualTo(401);
        }

        assertThat(desafios.buscar(desafioId)).isEmpty();
        assertThat(confirmar(ROTA_CONFIRMACAO_CLIENTES, desafioId, enviador.ultimoCodigo()).getStatusCode())
            .isEqualTo(401);
    }

    @Test
    void desafioVenceDepoisDeCincoMinutos() {
        var desafioId = desafioId(iniciar(ROTA_CLIENTES, CLIENTE_ATIVO));
        relogio.avancar(Duration.ofMinutes(5).plusSeconds(1));

        var resposta = confirmar(ROTA_CONFIRMACAO_CLIENTES, desafioId, enviador.ultimoCodigo());

        assertThat(resposta.getStatusCode()).isEqualTo(401);
        assertThat(desafios.buscar(desafioId)).isEmpty();
        assertThat(contexto.log()).contains("resultado=expirado");
    }

    @Test
    void desafioAindaValeNoLimiteDosCincoMinutos() {
        var desafioId = desafioId(iniciar(ROTA_CLIENTES, CLIENTE_ATIVO));
        relogio.avancar(Duration.ofMinutes(5));

        assertThat(confirmar(ROTA_CONFIRMACAO_CLIENTES, desafioId, enviador.ultimoCodigo()).getStatusCode())
            .isEqualTo(200);
    }

    @Test
    void desafioInexistenteDevolve401() {
        var resposta = confirmar(ROTA_CONFIRMACAO_CLIENTES, UUID.randomUUID().toString(), "123456");

        assertThat(resposta.getStatusCode()).isEqualTo(401);
        assertThat(contexto.log()).contains("resultado=desafio_indisponivel");
    }

    @Test
    void desafioForaDoFormatoDevolve401SemConsultarORepositorio() {
        desafios.falharCom(new IllegalStateException("não deveria consultar"));

        var resposta = confirmar(ROTA_CONFIRMACAO_CLIENTES, "nao-e-um-uuid", "123456");

        assertThat(resposta.getStatusCode()).isEqualTo(401);
        assertThat(contexto.log()).contains("resultado=desafio_invalido");
    }

    @Test
    void todasAsFalhasDeConfirmacaoTemRespostaIdentica() {
        var respostas = new ArrayList<APIGatewayV2HTTPResponse>();
        respostas.add(confirmar(ROTA_CONFIRMACAO_CLIENTES, UUID.randomUUID().toString(), "123456"));
        respostas.add(confirmar(ROTA_CONFIRMACAO_CLIENTES, "nao-e-um-uuid", "123456"));
        respostas.add(confirmar(ROTA_CONFIRMACAO_CLIENTES, desafioId(iniciar(ROTA_CLIENTES, SEM_CADASTRO)), "123456"));
        respostas.add(confirmar(ROTA_CONFIRMACAO_CLIENTES,
            desafioId(iniciar(ROTA_CLIENTES, CLIENTE_ATIVO)), codigoErrado()));
        respostas.add(confirmar(ROTA_CONFIRMACAO_FUNCIONARIOS,
            desafioId(iniciar(ROTA_CLIENTES, CLIENTE_ATIVO)), enviador.ultimoCodigo()));

        var esgotado = desafioId(iniciar(ROTA_CLIENTES, CLIENTE_ATIVO));
        for (int i = 0; i < 3; i++) {
            confirmar(ROTA_CONFIRMACAO_CLIENTES, esgotado, codigoErrado());
        }
        respostas.add(confirmar(ROTA_CONFIRMACAO_CLIENTES, esgotado, enviador.ultimoCodigo()));

        var vencido = desafioId(iniciar(ROTA_CLIENTES, CLIENTE_ATIVO));
        relogio.avancar(Duration.ofMinutes(6));
        respostas.add(confirmar(ROTA_CONFIRMACAO_CLIENTES, vencido, enviador.ultimoCodigo()));

        for (var resposta : respostas) {
            assertThat(resposta.getStatusCode()).isEqualTo(401);
            assertThat(resposta.getHeaders()).isEqualTo(respostas.get(0).getHeaders());
            assertThat(resposta.getBody()).isEqualTo(respostas.get(0).getBody());
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "{", "{\"desafioId\":\"x\"}", "{\"codigo\":\"123456\"}", "{\"desafioId\":\" \",\"codigo\":\"1\"}"})
    void confirmacaoSemDesafioOuCodigoDevolve400(String corpo) throws Exception {
        var resposta = handler.handleRequest(evento(ROTA_CONFIRMACAO_CLIENTES, corpo), contexto);

        assertThat(resposta.getStatusCode()).isEqualTo(400);
        assertThat(corpo(resposta).get("erro").asText()).isEqualTo("REQUISICAO_INVALIDA");
    }

    @Test
    void funcionarioCompletaAsDuasEtapasPelasRotasDeFuncionario() {
        var desafioId = desafioId(iniciar(ROTA_FUNCIONARIOS, FUNCIONARIO_ATIVO));

        assertThat(identidades.consultas).containsExactly(Papel.FUNCIONARIO);
        assertThat(enviador.enviados).singleElement()
            .satisfies(envio -> assertThat(envio.destinatario()).isEqualTo("maria@example.com"));

        var resposta = confirmar(ROTA_CONFIRMACAO_FUNCIONARIOS, desafioId, enviador.ultimoCodigo());

        assertThat(resposta.getStatusCode()).isEqualTo(200);
        var claims = claims(resposta);
        assertThat(claims.getSubject()).isEqualTo(ID_FUNCIONARIO.toString());
        assertThat(claims.get("role")).isEqualTo("FUNCIONARIO");
    }

    @Test
    void funcionarioInativoRecebeAMesmaRespostaSemEnvio() {
        var ativo = iniciar(ROTA_FUNCIONARIOS, FUNCIONARIO_ATIVO);
        var inativo = iniciar(ROTA_FUNCIONARIOS, FUNCIONARIO_INATIVO);

        assertThat(inativo.getStatusCode()).isEqualTo(ativo.getStatusCode()).isEqualTo(202);
        assertThat(enviador.enviados).hasSize(1);
        assertThat(contexto.log()).contains("resultado=sem_permissao papel=FUNCIONARIO");
    }

    @Test
    void desafioSoEConfirmadoNaRotaDoMesmoPapel() {
        var deCliente = desafioId(iniciar(ROTA_CLIENTES, CLIENTE_ATIVO));
        var codigoCliente = enviador.ultimoCodigo();
        var deFuncionario = desafioId(iniciar(ROTA_FUNCIONARIOS, FUNCIONARIO_ATIVO));
        var codigoFuncionario = enviador.ultimoCodigo();

        assertThat(confirmar(ROTA_CONFIRMACAO_FUNCIONARIOS, deCliente, codigoCliente).getStatusCode())
            .isEqualTo(401);
        assertThat(confirmar(ROTA_CONFIRMACAO_CLIENTES, deFuncionario, codigoFuncionario).getStatusCode())
            .isEqualTo(401);
        assertThat(contexto.log()).contains("resultado=papel_divergente");
    }

    @Test
    void logNuncaContemCpfCompletoCodigoNemEmail() {
        var desafioId = desafioId(iniciar(ROTA_CLIENTES, CLIENTE_ATIVO));
        var codigo = enviador.ultimoCodigo();
        confirmar(ROTA_CONFIRMACAO_CLIENTES, desafioId, codigoErrado());
        confirmar(ROTA_CONFIRMACAO_CLIENTES, desafioId, codigo);
        iniciar(ROTA_CLIENTES, SEM_CADASTRO);

        assertThat(contexto.linhas).hasSizeGreaterThanOrEqualTo(4);
        assertThat(contexto.log())
            .contains("cpf=***725", "desafio=" + desafioId.substring(0, 8))
            .doesNotContain("52998224725", "39053344705", codigo, "joao@example.com", desafioId);
    }

    @Test
    void rotaNaoAtendidaDevolve404() throws Exception {
        var resposta = handler.handleRequest(evento("GET /auth", "{}"), contexto);

        assertThat(resposta.getStatusCode()).isEqualTo(404);
        assertThat(corpo(resposta).get("erro").asText()).isEqualTo("ROTA_INEXISTENTE");
        assertThat(handler.handleRequest(null, contexto).getStatusCode()).isEqualTo(404);
        assertThat(handler.handleRequest(APIGatewayV2HTTPEvent.builder().build(), null).getStatusCode())
            .isEqualTo(404);
    }

    @Test
    void funcionaSemContextoDeLog() {
        var desafioId = desafioId(handler.handleRequest(
            evento(ROTA_CLIENTES, "{\"cpf\":\"" + CLIENTE_ATIVO.valor() + "\"}"), null));

        var resposta = handler.handleRequest(evento(ROTA_CONFIRMACAO_CLIENTES,
            "{\"desafioId\":\"" + desafioId + "\",\"codigo\":\"" + enviador.ultimoCodigo() + "\"}"), null);

        assertThat(resposta.getStatusCode()).isEqualTo(200);
    }

    private APIGatewayV2HTTPResponse iniciar(String rota, Cpf cpf) {
        return handler.handleRequest(evento(rota, "{\"cpf\":\"" + cpf.valor() + "\"}"), contexto);
    }

    private APIGatewayV2HTTPResponse confirmar(String rota, String desafioId, String codigo) {
        return handler.handleRequest(
            evento(rota, "{\"desafioId\":\"" + desafioId + "\",\"codigo\":\"" + codigo + "\"}"), contexto);
    }

    private String codigoErrado() {
        var certo = Integer.parseInt(enviador.ultimoCodigo());
        return String.format(Locale.ROOT, "%06d", (certo + 1) % 1_000_000);
    }

    private static APIGatewayV2HTTPEvent evento(String rota, String corpo) {
        return APIGatewayV2HTTPEvent.builder()
            .withRouteKey(rota)
            .withBody(corpo)
            .build();
    }

    private static JsonNode corpo(APIGatewayV2HTTPResponse resposta) {
        try {
            return JSON.readTree(resposta.getBody());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String desafioId(APIGatewayV2HTTPResponse resposta) {
        return corpo(resposta).get("desafioId").asText();
    }

    private static Claims claims(APIGatewayV2HTTPResponse resposta) {
        return new TokenValidator(SEGREDO_JWT).validar(corpo(resposta).get("accessToken").asText()).orElseThrow();
    }
}
