package com.oficina.auth.identidade;

public class ConsultaIdentidadeException extends RuntimeException {

    public ConsultaIdentidadeException(String mensagem) {
        super(mensagem);
    }

    public ConsultaIdentidadeException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
