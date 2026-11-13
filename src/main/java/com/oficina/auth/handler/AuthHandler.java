package com.oficina.auth.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.auth.config.ConfiguracaoAutenticacao;
import com.oficina.auth.cpf.Cpf;
import com.oficina.auth.cpf.CpfInvalidoException;
import com.oficina.auth.desafio.Desafio;
import com.oficina.auth.desafio.GeradorCodigo;
import com.oficina.auth.desafio.HashCodigo;
import com.oficina.auth.desafio.RepositorioDesafios;
import com.oficina.auth.desafio.RepositorioDesafiosDynamo;
import com.oficina.auth.dto.AutenticacaoRequest;
import com.oficina.auth.dto.ConfirmacaoRequest;
import com.oficina.auth.dto.DesafioResponse;
import com.oficina.auth.dto.ErroResponse;
import com.oficina.auth.dto.TokenResponse;
import com.oficina.auth.identidade.Cadastro;
import com.oficina.auth.identidade.ConsultaIdentidade;
import com.oficina.auth.identidade.ConsultaIdentidadeException;
import com.oficina.auth.identidade.ConsultaIdentidadeHttp;
import com.oficina.auth.identidade.Papel;
import com.oficina.auth.notificacao.EnviadorCodigo;
import com.oficina.auth.notificacao.EnviadorCodigoSes;
import com.oficina.auth.notificacao.EnvioCodigoException;
import com.oficina.auth.token.TokenIssuer;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public class AuthHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final String ROTA_CLIENTES = "POST /auth";
    static final String ROTA_CONFIRMACAO_CLIENTES = "POST /auth/confirmacao";
    static final String ROTA_FUNCIONARIOS = "POST /auth/funcionarios";
    static final String ROTA_CONFIRMACAO_FUNCIONARIOS = "POST /auth/funcionarios/confirmacao";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern FORMATO_UUID =
        Pattern.compile("\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}");

    private final ConsultaIdentidade identidades;
    private final RepositorioDesafios desafios;
    private final EnviadorCodigo enviador;
    private final GeradorCodigo gerador;
    private final HashCodigo hash;
    private final TokenIssuer emissor;
    private final Clock relogio;

    public AuthHandler() {
        this(ConfiguracaoAutenticacao.carregar());
    }

    private AuthHandler(ConfiguracaoAutenticacao config) {
        this(new ConsultaIdentidadeHttp(config.urlOsService(), config.chaveInterna()),
            new RepositorioDesafiosDynamo(DynamoDbClient.create(), config.tabelaDesafios()),
            new EnviadorCodigoSes(SesV2Client.create(), config.remetenteEmail()),
            new GeradorCodigo(),
            new HashCodigo(config.otpSegredo()),
            new TokenIssuer(config.jwtSecret()),
            Clock.systemUTC());
    }

    AuthHandler(ConsultaIdentidade identidades, RepositorioDesafios desafios, EnviadorCodigo enviador,
                GeradorCodigo gerador, HashCodigo hash, TokenIssuer emissor, Clock relogio) {
        this.identidades = identidades;
        this.desafios = desafios;
        this.enviador = enviador;
        this.gerador = gerador;
        this.hash = hash;
        this.emissor = emissor;
        this.relogio = relogio;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context context) {
        var rota = evento == null || evento.getRouteKey() == null ? "" : evento.getRouteKey();
        try {
            return switch (rota) {
                case ROTA_CLIENTES -> iniciar(evento, Papel.CLIENTE, context);
                case ROTA_FUNCIONARIOS -> iniciar(evento, Papel.FUNCIONARIO, context);
                case ROTA_CONFIRMACAO_CLIENTES -> confirmar(evento, Papel.CLIENTE, context);
                case ROTA_CONFIRMACAO_FUNCIONARIOS -> confirmar(evento, Papel.FUNCIONARIO, context);
                default -> responder(404, ErroResponse.rotaInexistente());
            };
        } catch (Exception e) {
            if (context != null) {
                context.getLogger().log("erro=ERRO_INTERNO detalhe=" + e.getMessage());
            }
            return responder(500, ErroResponse.interno());
        }
    }

    private APIGatewayV2HTTPResponse iniciar(APIGatewayV2HTTPEvent evento, Papel papel, Context context) {
        Cpf cpf;
        try {
            cpf = Cpf.de(ler(evento, AutenticacaoRequest.class)
                .orElseThrow(() -> new CpfInvalidoException("Corpo da requisição ausente ou inválido."))
                .cpf());
        } catch (CpfInvalidoException e) {
            return responder(400, ErroResponse.cpfInvalido(e.getMessage()));
        }

        Optional<Cadastro> cadastro;
        try {
            cadastro = identidades.buscar(papel, cpf);
        } catch (ConsultaIdentidadeException e) {
            new Registro(context, papel).cpf(cpf).logar("os_indisponivel");
            return responder(503, ErroResponse.servicoIndisponivel());
        }

        if (cadastro.isEmpty()) {
            return responderSemEnviar(new Registro(context, papel).cpf(cpf), "nao_encontrado");
        }
        var titular = cadastro.get().identidade();
        var registro = new Registro(context, papel).cpf(cpf).id(titular.id());
        if (!titular.podeAutenticar()) {
            return responderSemEnviar(registro, "sem_permissao");
        }
        var email = cadastro.get().email();
        if (email == null || email.isBlank()) {
            return responderSemEnviar(registro, "sem_email");
        }

        var codigo = gerador.gerar();
        var desafioId = UUID.randomUUID().toString();
        registro.desafio(desafioId);
        desafios.salvar(Desafio.novo(desafioId, titular, cpf, hash.calcular(desafioId, codigo), relogio.instant()));
        try {
            enviador.enviar(email, titular.nome(), codigo);
        } catch (EnvioCodigoException e) {
            desafios.remover(desafioId);
            registro.logar("envio_falhou");
            return responder(503, ErroResponse.servicoIndisponivel());
        }

        registro.logar("codigo_enviado");
        return responder(202, DesafioResponse.emitido(desafioId));
    }

    private APIGatewayV2HTTPResponse responderSemEnviar(Registro registro, String motivo) {
        registro.logar(motivo);
        return responder(202, DesafioResponse.emitido(UUID.randomUUID().toString()));
    }

    private APIGatewayV2HTTPResponse confirmar(APIGatewayV2HTTPEvent evento, Papel papel, Context context) {
        var pedido = ler(evento, ConfirmacaoRequest.class).orElse(null);
        if (pedido == null || vazio(pedido.desafioId()) || vazio(pedido.codigo())) {
            return responder(400, ErroResponse.requisicaoInvalida("Informe desafioId e codigo."));
        }

        var registro = new Registro(context, papel);
        var desafioId = pedido.desafioId();
        if (!FORMATO_UUID.matcher(desafioId).matches()) {
            return recusar(registro, "desafio_invalido");
        }
        registro.desafio(desafioId);

        var registrado = desafios.registrarTentativa(desafioId);
        if (registrado.isEmpty()) {
            desafios.remover(desafioId);
            return recusar(registro, "desafio_indisponivel");
        }

        var desafio = registrado.get();
        registro.cpf(desafio.cpf()).id(desafio.titular().id());
        if (desafio.papel() != papel) {
            return recusar(registro, "papel_divergente");
        }
        if (desafio.expirado(relogio.instant())) {
            desafios.remover(desafioId);
            return recusar(registro, "expirado");
        }
        if (!hash.confere(desafioId, pedido.codigo(), desafio.codigoHash())) {
            if (desafio.tentativasEsgotadas()) {
                desafios.remover(desafioId);
            }
            return recusar(registro, "codigo_incorreto");
        }
        if (!desafios.remover(desafioId)) {
            return recusar(registro, "desafio_ja_utilizado");
        }

        registro.logar("autenticado");
        return responder(200, TokenResponse.bearer(
            emissor.emitir(desafio.titular(), desafio.cpf()), TokenIssuer.EXPIRACAO_SEGUNDOS));
    }

    private APIGatewayV2HTTPResponse recusar(Registro registro, String motivo) {
        registro.logar(motivo);
        return responder(401, ErroResponse.autenticacaoRecusada());
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static <T> Optional<T> ler(APIGatewayV2HTTPEvent evento, Class<T> tipo) {
        if (vazio(evento.getBody())) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(JSON.readValue(evento.getBody(), tipo));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private APIGatewayV2HTTPResponse responder(int status, Object corpo) {
        try {
            return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(JSON.writeValueAsString(corpo))
                .build();
        } catch (Exception e) {
            return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(500)
                .withBody("{\"erro\":\"ERRO_INTERNO\"}")
                .build();
        }
    }

    private static final class Registro {

        private final Context context;
        private final Papel papel;
        private Cpf cpf;
        private UUID id;
        private String desafio;

        Registro(Context context, Papel papel) {
            this.context = context;
            this.papel = papel;
        }

        Registro cpf(Cpf cpf) {
            this.cpf = cpf;
            return this;
        }

        Registro id(UUID id) {
            this.id = id;
            return this;
        }

        Registro desafio(String desafioId) {
            this.desafio = desafioId.substring(0, 8);
            return this;
        }

        void logar(String resultado) {
            if (context == null) {
                return;
            }
            context.getLogger().log(String.format(
                "resultado=%s papel=%s cpf=%s id=%s desafio=%s",
                resultado, papel,
                cpf == null ? "-" : cpf.mascarado(),
                id == null ? "-" : id,
                desafio == null ? "-" : desafio));
        }
    }
}
