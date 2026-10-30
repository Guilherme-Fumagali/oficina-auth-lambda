package com.oficina.auth.cliente;

public enum StatusCliente {
    ATIVO,
    INATIVO,
    BLOQUEADO;

    public boolean permiteAutenticacao() {
        return this == ATIVO;
    }

    public static StatusCliente de(String valor) {
        try {
            return valueOf(valor);
        } catch (IllegalArgumentException | NullPointerException e) {
            return BLOQUEADO;
        }
    }
}
