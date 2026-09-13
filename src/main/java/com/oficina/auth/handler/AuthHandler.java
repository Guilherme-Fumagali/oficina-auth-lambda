package com.oficina.auth.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.auth.cliente.Cliente;
import com.oficina.auth.cliente.ClienteRepository;
import com.oficina.auth.config.Configuracao;
import com.oficina.auth.cpf.Cpf;
import com.oficina.auth.cpf.CpfInvalidoException;
import com.oficina.auth.dto.AutenticacaoRequest;
import com.oficina.auth.dto.ErroResponse;
import com.oficina.auth.dto.TokenResponse;
import com.oficina.auth.token.TokenIssuer;

import java.util.Map;
import java.util.Optional;

public class AuthHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ClienteRepository repositorio;
    private final TokenIssuer emissor;

    public AuthHandler() {
        var config = Configuracao.carregar();
        this.repositorio = new ClienteRepository(config.jdbcUrl(), config.dbUsuario(), config.dbSenha());
        this.emissor = new TokenIssuer(config.jwtSecret());
    }

    AuthHandler(ClienteRepository repositorio, TokenIssuer emissor) {
        this.repositorio = repositorio;
        this.emissor = emissor;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context context) {
        Cpf cpf;
        try {
            cpf = Cpf.de(extrairCpf(evento));
        } catch (CpfInvalidoException e) {
            return responder(400, ErroResponse.cpfInvalido(e.getMessage()));
        }

        try {
            Optional<Cliente> encontrado = repositorio.buscarPorCpf(cpf);

            if (encontrado.isEmpty()) {
                logar(context, "cliente_nao_encontrado", cpf, null);
                return responder(404, ErroResponse.clienteNaoEncontrado());
            }

            var cliente = encontrado.get();
            if (!cliente.podeAutenticar()) {
                logar(context, "cliente_sem_permissao", cpf, cliente);
                return responder(403, ErroResponse.clienteInativo());
            }

            logar(context, "autenticado", cpf, cliente);
            return responder(200, TokenResponse.bearer(
                emissor.emitir(cliente, cpf), TokenIssuer.EXPIRACAO_SEGUNDOS));

        } catch (Exception e) {
            if (context != null) {
                context.getLogger().log("erro=ERRO_INTERNO detalhe=" + e.getMessage());
            }
            return responder(500, ErroResponse.interno());
        }
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

    private void logar(Context context, String resultado, Cpf cpf, Cliente cliente) {
        if (context == null) {
            return;
        }
        context.getLogger().log(String.format(
            "resultado=%s cpf=%s cliente=%s",
            resultado, cpf.mascarado(), cliente == null ? "-" : cliente.id()));
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
