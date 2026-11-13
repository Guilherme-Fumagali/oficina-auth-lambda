package com.oficina.auth.desafio;

import com.oficina.auth.cpf.Cpf;
import com.oficina.auth.identidade.Identidade;
import com.oficina.auth.identidade.Papel;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record Desafio(
        String id,
        Identidade titular,
        Cpf cpf,
        String codigoHash,
        int tentativas,
        Instant expiraEm) {

    public static final Duration VALIDADE = Duration.ofMinutes(5);
    public static final int MAXIMO_TENTATIVAS = 3;

    public static Desafio novo(String id, Identidade titular, Cpf cpf, String codigoHash, Instant agora) {
        return new Desafio(id, titular, cpf, codigoHash, 0,
            agora.plus(VALIDADE).truncatedTo(ChronoUnit.SECONDS));
    }

    public Papel papel() {
        return titular.papel();
    }

    public boolean expirado(Instant agora) {
        return expiraEm.isBefore(agora);
    }

    public boolean tentativasEsgotadas() {
        return tentativas >= MAXIMO_TENTATIVAS;
    }
}
