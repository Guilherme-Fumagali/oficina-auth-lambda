package com.oficina.auth.token;

import com.oficina.auth.cpf.Cpf;
import com.oficina.auth.identidade.Identidade;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public class TokenIssuer {

    public static final String ISSUER = "oficina-auth";
    public static final long EXPIRACAO_SEGUNDOS = 900;

    private final SecretKey chave;

    public TokenIssuer(String segredo) {
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                "Segredo do JWT precisa de no mínimo 32 bytes para HMAC-SHA256.");
        }
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
    }

    public String emitir(Identidade identidade, Cpf cpf) {
        var agora = Instant.now();
        return Jwts.builder()
            .subject(identidade.id().toString())
            .issuer(ISSUER)
            .claim("cpf", cpf.valor())
            .claim("nome", identidade.nome())
            .claim("role", identidade.papel().name())
            .issuedAt(Date.from(agora))
            .expiration(Date.from(agora.plusSeconds(EXPIRACAO_SEGUNDOS)))
            .signWith(chave)
            .compact();
    }
}
