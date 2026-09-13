package com.oficina.auth.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2CustomAuthorizerEvent;
import com.oficina.auth.config.Configuracao;
import com.oficina.auth.token.TokenValidator;

import java.util.HashMap;
import java.util.Map;

public class AuthorizerHandler
        implements RequestHandler<APIGatewayV2CustomAuthorizerEvent, Map<String, Object>> {

    private final TokenValidator validador;

    public AuthorizerHandler() {
        this.validador = new TokenValidator(Configuracao.carregar().jwtSecret());
    }

    AuthorizerHandler(TokenValidator validador) {
        this.validador = validador;
    }

    @Override
    public Map<String, Object> handleRequest(APIGatewayV2CustomAuthorizerEvent evento, Context context) {
        var token = TokenValidator.extrairBearer(cabecalhoAutorizacao(evento));

        return validador.validar(token)
            .map(claims -> {
                var contexto = new HashMap<String, Object>();
                contexto.put("clienteId", claims.getSubject());
                contexto.put("role", String.valueOf(claims.get("role")));
                return autorizado(true, contexto);
            })
            .orElseGet(() -> autorizado(false, Map.of()));
    }

    private String cabecalhoAutorizacao(APIGatewayV2CustomAuthorizerEvent evento) {
        if (evento == null || evento.getHeaders() == null) {
            return null;
        }
        return evento.getHeaders().entrySet().stream()
            .filter(e -> "authorization".equalsIgnoreCase(e.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private Map<String, Object> autorizado(boolean permitido, Map<String, Object> contexto) {
        return Map.of("isAuthorized", permitido, "context", contexto);
    }
}
