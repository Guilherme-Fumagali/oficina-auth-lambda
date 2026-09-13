package com.oficina.auth.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class TokenValidator {

    private final SecretKey chave;

    public TokenValidator(String segredo) {
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<Claims> validar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            var claims = Jwts.parser()
                .verifyWith(chave)
                .requireIssuer(TokenIssuer.ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return Optional.of(claims);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static String extrairBearer(String headerAuthorization) {
        if (headerAuthorization == null) {
            return null;
        }
        var valor = headerAuthorization.trim();
        if (valor.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return valor.substring(7).trim();
        }
        return null;
    }
}
