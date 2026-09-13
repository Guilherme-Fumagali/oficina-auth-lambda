package com.oficina.auth.config;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

public final class Configuracao {

    private static final String PREFIXO = System.getenv().getOrDefault("SSM_PREFIX", "/oficina/prod");

    private static Configuracao instancia;

    private final String jwtSecret;
    private final String jdbcUrl;
    private final String dbUsuario;
    private final String dbSenha;

    private Configuracao(String jwtSecret, String jdbcUrl, String dbUsuario, String dbSenha) {
        this.jwtSecret = jwtSecret;
        this.jdbcUrl = jdbcUrl;
        this.dbUsuario = dbUsuario;
        this.dbSenha = dbSenha;
    }

    public static synchronized Configuracao carregar() {
        if (instancia == null) {
            instancia = System.getenv("JWT_SECRET") != null
                ? doAmbiente()
                : doSsm();
        }
        return instancia;
    }

    private static Configuracao doAmbiente() {
        return new Configuracao(
            System.getenv("JWT_SECRET"),
            System.getenv("DB_URL"),
            System.getenv("DB_USER"),
            System.getenv("DB_PASSWORD"));
    }

    private static Configuracao doSsm() {
        try (var ssm = SsmClient.create()) {
            return new Configuracao(
                ler(ssm, PREFIXO + "/jwt-secret", true),
                "jdbc:postgresql://" + ler(ssm, PREFIXO + "/db-endpoint", false)
                    + "/" + ler(ssm, PREFIXO + "/db-name", false),
                ler(ssm, PREFIXO + "/db-username", false),
                ler(ssm, PREFIXO + "/db-password", true));
        }
    }

    private static String ler(SsmClient ssm, String nome, boolean cifrado) {
        return ssm.getParameter(GetParameterRequest.builder()
                .name(nome)
                .withDecryption(cifrado)
                .build())
            .parameter()
            .value();
    }

    public String jwtSecret() { return jwtSecret; }
    public String jdbcUrl()   { return jdbcUrl; }
    public String dbUsuario() { return dbUsuario; }
    public String dbSenha()   { return dbSenha; }
}
