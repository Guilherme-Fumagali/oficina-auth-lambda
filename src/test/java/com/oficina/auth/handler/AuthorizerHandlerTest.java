package com.oficina.auth.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2CustomAuthorizerEvent;
import com.oficina.auth.cliente.Cliente;
import com.oficina.auth.cliente.StatusCliente;
import com.oficina.auth.cpf.Cpf;
import com.oficina.auth.token.TokenIssuer;
import com.oficina.auth.token.TokenValidator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizerHandlerTest {

    private static final String SEGREDO = "chave-de-teste-super-longa-para-hmac-sha-256-minimo-32-chars";

    private final AuthorizerHandler handler = new AuthorizerHandler(new TokenValidator(SEGREDO));

    @Test
    void deveAutorizarTokenValidoEDevolverContexto() {
        var id = UUID.randomUUID();
        var token = new TokenIssuer(SEGREDO).emitir(
            new Cliente(id, "João", StatusCliente.ATIVO), Cpf.de("52998224725"));

        var resposta = handler.handleRequest(evento("Bearer " + token), null);

        assertThat(resposta.get("isAuthorized")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        var contexto = (Map<String, Object>) resposta.get("context");
        assertThat(contexto.get("clienteId")).isEqualTo(id.toString());
        assertThat(contexto.get("role")).isEqualTo("CLIENTE");
    }

    @Test
    void deveAceitarHeaderComQualquerCapitalizacao() {
        var token = new TokenIssuer(SEGREDO).emitir(
            new Cliente(UUID.randomUUID(), "João", StatusCliente.ATIVO), Cpf.de("52998224725"));

        var evento = APIGatewayV2CustomAuthorizerEvent.builder()
            .withHeaders(Map.of("AUTHORIZATION", "Bearer " + token))
            .build();

        assertThat(handler.handleRequest(evento, null).get("isAuthorized")).isEqualTo(true);
    }

    @Test
    void deveNegarSemHeaderDeAutorizacao() {
        var evento = APIGatewayV2CustomAuthorizerEvent.builder().withHeaders(Map.of()).build();
        assertThat(handler.handleRequest(evento, null).get("isAuthorized")).isEqualTo(false);
    }

    @Test
    void deveNegarTokenDeOutraChave() {
        var token = new TokenIssuer("outra-chave-completamente-diferente-com-32-chars-no-minimo!!")
            .emitir(new Cliente(UUID.randomUUID(), "João", StatusCliente.ATIVO), Cpf.de("52998224725"));

        assertThat(handler.handleRequest(evento("Bearer " + token), null).get("isAuthorized"))
            .isEqualTo(false);
    }

    @Test
    void deveNegarEsquemaDiferenteDeBearer() {
        assertThat(handler.handleRequest(evento("Basic dXNlcjpwYXNz"), null).get("isAuthorized"))
            .isEqualTo(false);
    }

    private APIGatewayV2CustomAuthorizerEvent evento(String authorization) {
        return APIGatewayV2CustomAuthorizerEvent.builder()
            .withHeaders(Map.of("authorization", authorization))
            .build();
    }
}
