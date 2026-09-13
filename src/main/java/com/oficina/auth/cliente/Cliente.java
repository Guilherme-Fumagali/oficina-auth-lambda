package com.oficina.auth.cliente;

import java.util.UUID;

public record Cliente(UUID id, String nome, StatusCliente status) {

    public boolean podeAutenticar() {
        return status != null && status.permiteAutenticacao();
    }
}
