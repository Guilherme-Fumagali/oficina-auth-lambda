package com.oficina.auth.config;

import java.util.Map;

public final class ConfiguracaoAutorizador {

    private static ConfiguracaoAutorizador instancia;

    private final String jwtSecret;

    private ConfiguracaoAutorizador(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public static synchronized ConfiguracaoAutorizador carregar() {
        if (instancia == null) {
            instancia = de(FonteConfiguracao.doSistema());
        }
        return instancia;
    }

    static ConfiguracaoAutorizador de(FonteConfiguracao fonte) {
        return new ConfiguracaoAutorizador(
            fonte.parametros(Map.of("jwt-secret", "JWT_SECRET")).get("jwt-secret"));
    }

    public String jwtSecret() { return jwtSecret; }
}
