package com.oficina.auth.desafio;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.random.RandomGenerator;

public class GeradorCodigo {

    static final int LIMITE = 1_000_000;

    private final RandomGenerator aleatorio;

    public GeradorCodigo() {
        this(new SecureRandom());
    }

    GeradorCodigo(RandomGenerator aleatorio) {
        this.aleatorio = aleatorio;
    }

    public String gerar() {
        return String.format(Locale.ROOT, "%06d", aleatorio.nextInt(LIMITE));
    }
}
