package com.oficina.auth.notificacao;

public class EnvioCodigoException extends RuntimeException {

    public EnvioCodigoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
