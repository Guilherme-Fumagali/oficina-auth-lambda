package com.oficina.auth.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.auth.config.ConfiguracaoAutenticacao;
import com.oficina.auth.cpf.Cpf;
import com.oficina.auth.cpf.CpfInvalidoException;
import com.oficina.auth.dto.AutenticacaoRequest;
import com.oficina.auth.dto.ErroResponse;
import com.oficina.auth.dto.TokenResponse;
import com.oficina.auth.identidade.ConsultaIdentidade;
import com.oficina.auth.identidade.ConsultaIdentidadeException;
import com.oficina.auth.identidade.ConsultaIdentidadeHttp;
import com.oficina.auth.identidade.Identidade;
import com.oficina.auth.identidade.Papel;
import com.oficina.auth.token.TokenIssuer;

import java.util.Map;

public class AuthHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final String ROTA_FUNCIONARIOS = "POST /auth/funcionarios";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ConsultaIdentidade identidades;
    private final TokenIssuer emissor;

    public AuthHandler() {
        this(ConfiguracaoAutenticacao.carregar());
    }

    private AuthHandler(ConfiguracaoAutenticacao config) {
        this(new ConsultaIdentidadeHttp(config.urlOsService(), config.chaveInterna()),
            new TokenIssuer(config.jwtSecret()));
    }

    AuthHandler(ConsultaIdentidade identidades, TokenIssuer emissor) {
        this.identidades = identidades;
        this.emissor = emissor;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context context) {
        var papel = papelDaRota(evento);
        Cpf cpf;
        try {
            cpf = Cpf.de(extrairCpf(evento));
        } catch (CpfInvalidoException e) {
            return responder(400, ErroResponse.cpfInvalido(e.getMessage()));
        }

        try {
            var encontrado = identidades.buscar(papel, cpf);

            if (encontrado.isEmpty()) {
                logar(context, "nao_encontrado", papel, cpf, null);
                return responder(401, ErroResponse.autenticacaoRecusada());
            }

            var identidade = encontrado.get().identidade();
            if (!identidade.podeAutenticar()) {
                logar(context, "sem_permissao", papel, cpf, identidade);
                return responder(401, ErroResponse.autenticacaoRecusada());
            }

            logar(context, "autenticado", papel, cpf, identidade);
            return responder(200, TokenResponse.bearer(
                emissor.emitir(identidade, cpf), TokenIssuer.EXPIRACAO_SEGUNDOS));

        } catch (ConsultaIdentidadeException e) {
            logar(context, "os_indisponivel", papel, cpf, null);
            return responder(503, ErroResponse.servicoIndisponivel());
        } catch (Exception e) {
            if (context != null) {
                context.getLogger().log("erro=ERRO_INTERNO detalhe=" + e.getMessage());
            }
            return responder(500, ErroResponse.interno());
        }
    }

    private Papel papelDaRota(APIGatewayV2HTTPEvent evento) {
        return evento != null && ROTA_FUNCIONARIOS.equals(evento.getRouteKey())
            ? Papel.FUNCIONARIO
            : Papel.CLIENTE;
    }

    private String extrairCpf(APIGatewayV2HTTPEvent evento) {
        if (evento == null || evento.getBody() == null || evento.getBody().isBlank()) {
            throw new CpfInvalidoException("Corpo da requisição ausente.");
        }
        try {
            var corpo = JSON.readValue(evento.getBody(), AutenticacaoRequest.class);
            return corpo.cpf();
        } catch (CpfInvalidoException e) {
            throw e;
        } catch (Exception e) {
            throw new CpfInvalidoException("Corpo da requisição inválido.");
        }
    }

    private void logar(Context context, String resultado, Papel papel, Cpf cpf, Identidade identidade) {
        if (context == null) {
            return;
        }
        context.getLogger().log(String.format(
            "resultado=%s papel=%s cpf=%s id=%s",
            resultado, papel, cpf.mascarado(), identidade == null ? "-" : identidade.id()));
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
}
