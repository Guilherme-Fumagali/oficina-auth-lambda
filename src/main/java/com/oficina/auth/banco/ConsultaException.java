package com.oficina.auth.banco;

public class ConsultaException extends RuntimeException {

    public ConsultaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
