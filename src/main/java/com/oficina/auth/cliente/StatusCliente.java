package com.oficina.auth.cliente;

/** Espelha o enum e a constraint {@code chk_cliente_status} da migration V6. */
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
