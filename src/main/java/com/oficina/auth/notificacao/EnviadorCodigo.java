package com.oficina.auth.notificacao;

public interface EnviadorCodigo {

    void enviar(String destinatario, String nome, String codigo);
}
