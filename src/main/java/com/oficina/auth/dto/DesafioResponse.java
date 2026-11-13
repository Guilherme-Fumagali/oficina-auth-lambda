package com.oficina.auth.dto;

import com.oficina.auth.desafio.Desafio;

public record DesafioResponse(String desafioId, String mensagem, long expiraEmSegundos) {

    public static final String MENSAGEM =
        "Se o CPF estiver cadastrado e ativo, um código foi enviado ao e-mail registrado.";

    public static DesafioResponse emitido(String desafioId) {
        return new DesafioResponse(desafioId, MENSAGEM, Desafio.VALIDADE.toSeconds());
    }
}
