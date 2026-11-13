package com.oficina.auth.config;

import java.util.Map;

public final class ConfiguracaoAutenticacao {

    private static ConfiguracaoAutenticacao instancia;

    private final String jwtSecret;
    private final String chaveInterna;
    private final String otpSegredo;
    private final String urlOsService;
    private final String tabelaDesafios;
    private final String remetenteEmail;

    private ConfiguracaoAutenticacao(Map<String, String> parametros, String tabelaDesafios, String remetenteEmail) {
        this.jwtSecret = parametros.get("jwt-secret");
        this.chaveInterna = parametros.get("chave-interna");
        this.otpSegredo = parametros.get("otp-segredo");
        this.urlOsService = "http://" + parametros.get("nlb-dns");
        this.tabelaDesafios = tabelaDesafios;
        this.remetenteEmail = remetenteEmail;
    }

    public static synchronized ConfiguracaoAutenticacao carregar() {
        if (instancia == null) {
            instancia = de(FonteConfiguracao.doSistema());
        }
        return instancia;
    }

    static ConfiguracaoAutenticacao de(FonteConfiguracao fonte) {
        var tabelaDesafios = fonte.variavel("TABELA_DESAFIOS");
        var remetenteEmail = fonte.variavel("REMETENTE_EMAIL");
        var parametros = fonte.parametros(Map.of(
            "jwt-secret", "JWT_SECRET",
            "chave-interna", "CHAVE_INTERNA",
            "otp-segredo", "OTP_SEGREDO",
            "nlb-dns", "NLB_DNS"));
        return new ConfiguracaoAutenticacao(parametros, tabelaDesafios, remetenteEmail);
    }

    public String jwtSecret()      { return jwtSecret; }
    public String chaveInterna()   { return chaveInterna; }
    public String otpSegredo()     { return otpSegredo; }
    public String urlOsService()   { return urlOsService; }
    public String tabelaDesafios() { return tabelaDesafios; }
    public String remetenteEmail() { return remetenteEmail; }
}
