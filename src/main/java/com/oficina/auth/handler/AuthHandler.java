package com.oficina.auth.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.auth.banco.ConexaoJdbc;
import com.oficina.auth.cliente.ClienteRepository;
import com.oficina.auth.config.Configuracao;
import com.oficina.auth.cpf.Cpf;
import com.oficina.auth.cpf.CpfInvalidoException;
import com.oficina.auth.dto.AutenticacaoRequest;
import com.oficina.auth.dto.ErroResponse;
import com.oficina.auth.dto.TokenResponse;
import com.oficina.auth.funcionario.FuncionarioRepository;
import com.oficina.auth.identidade.Identidade;
import com.oficina.auth.identidade.Papel;
import com.oficina.auth.token.TokenIssuer;

import java.util.Map;
import java.util.Optional;

public class AuthHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final String ROTA_FUNCIONARIOS = "POST /auth/funcionarios";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ClienteRepository clientes;
    private final FuncionarioRepository funcionarios;
    private final TokenIssuer emissor;

    public AuthHandler() {
        var config = Configuracao.carregar();
        var conexao = new ConexaoJdbc(config.jdbcUrl(), config.dbUsuario(), config.dbSenha());
        this.clientes = new ClienteRepository(conexao);
        this.funcionarios = new FuncionarioRepository(conexao);
        this.emissor = new TokenIssuer(config.jwtSecret());
    }

    AuthHandler(ClienteRepository clientes, FuncionarioRepository funcionarios, TokenIssuer emissor) {
        this.clientes = clientes;
        this.funcionarios = funcionarios;
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
            Optional<? extends Identidade> encontrado = papel == Papel.FUNCIONARIO
                ? funcionarios.buscarPorCpf(cpf)
                : clientes.buscarPorCpf(cpf);

            if (encontrado.isEmpty()) {
                logar(context, "nao_encontrado", papel, cpf, null);
                return responder(401, ErroResponse.autenticacaoRecusada());
            }

            var identidade = encontrado.get();
            if (!identidade.podeAutenticar()) {
                logar(context, "sem_permissao", papel, cpf, identidade);
                return responder(401, ErroResponse.autenticacaoRecusada());
            }

            logar(context, "autenticado", papel, cpf, identidade);
            return responder(200, TokenResponse.bearer(
                emissor.emitir(identidade, cpf), TokenIssuer.EXPIRACAO_SEGUNDOS));

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
