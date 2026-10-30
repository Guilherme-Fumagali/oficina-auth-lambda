package com.oficina.auth.handler;

import com.oficina.auth.notificacao.EnviadorCodigo;
import com.oficina.auth.notificacao.EnvioCodigoException;

import java.util.ArrayList;
import java.util.List;

class EnviadorFake implements EnviadorCodigo {

    record Envio(String destinatario, String nome, String codigo) {}

    final List<Envio> enviados = new ArrayList<>();
    private boolean falhar;

    void falhar() {
        falhar = true;
    }

    String ultimoCodigo() {
        return enviados.get(enviados.size() - 1).codigo();
    }

    @Override
    public void enviar(String destinatario, String nome, String codigo) {
        if (falhar) {
            throw new EnvioCodigoException("Falha ao enviar o código por e-mail.", new RuntimeException("ses"));
        }
        enviados.add(new Envio(destinatario, nome, codigo));
    }
}
