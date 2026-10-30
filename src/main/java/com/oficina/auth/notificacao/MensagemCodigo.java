package com.oficina.auth.notificacao;

import java.time.Duration;

public record MensagemCodigo(String assunto, String corpo) {

    public static final String ASSUNTO = "Código de acesso — Oficina";

    public static MensagemCodigo para(String nome, String codigo, Duration validade) {
        var corpo = saudacao(nome) + "\n\n"
            + "Seu código de acesso é " + codigo + ".\n"
            + "Ele vale por " + validade.toMinutes() + " minutos e pode ser usado uma única vez.\n\n"
            + "Se você não solicitou o acesso, ignore este e-mail.\n";
        return new MensagemCodigo(ASSUNTO, corpo);
    }

    private static String saudacao(String nome) {
        if (nome == null || nome.isBlank()) {
            return "Olá.";
        }
        return "Olá, " + nome.trim().split("\\s+")[0] + ".";
    }
}
