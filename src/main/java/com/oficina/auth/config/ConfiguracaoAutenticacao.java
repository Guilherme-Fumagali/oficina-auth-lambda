package com.oficina.auth.config;

import java.util.Map;

public final class ConfiguracaoAutenticacao {

    private static ConfiguracaoAutenticacao instancia;

    private final String jwtSecret;
    private final String chaveInterna;
    private final String urlOsService;

    private ConfiguracaoAutenticacao(String jwtSecret, String chaveInterna, String urlOsService) {
        this.jwtSecret = jwtSecret;
        this.chaveInterna = chaveInterna;
        this.urlOsService = urlOsService;
    }

    public static synchronized ConfiguracaoAutenticacao carregar() {
        if (instancia == null) {
            instancia = de(FonteConfiguracao.doSistema());
        }
        return instancia;
    }

    static ConfiguracaoAutenticacao de(FonteConfiguracao fonte) {
        var parametros = fonte.parametros(Map.of(
            "jwt-secret", "JWT_SECRET",
            "chave-interna", "CHAVE_INTERNA",
            "nlb-dns", "NLB_DNS"));
        return new ConfiguracaoAutenticacao(
            parametros.get("jwt-secret"),
            parametros.get("chave-interna"),
            "http://" + parametros.get("nlb-dns"));
    }

    public String jwtSecret()    { return jwtSecret; }
    public String chaveInterna() { return chaveInterna; }
    public String urlOsService() { return urlOsService; }
}
