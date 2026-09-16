package com.oficina.auth.funcionario;

import com.oficina.auth.identidade.Identidade;
import com.oficina.auth.identidade.Papel;

import java.util.UUID;

public record Funcionario(UUID id, String nome, boolean ativo) implements Identidade {

    @Override
    public Papel papel() {
        return Papel.FUNCIONARIO;
    }

    @Override
    public boolean podeAutenticar() {
        return ativo;
    }
}
