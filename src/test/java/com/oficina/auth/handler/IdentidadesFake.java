package com.oficina.auth.handler;

import com.oficina.auth.cpf.Cpf;
import com.oficina.auth.identidade.Cadastro;
import com.oficina.auth.identidade.ConsultaIdentidade;
import com.oficina.auth.identidade.ConsultaIdentidadeException;
import com.oficina.auth.identidade.Papel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class IdentidadesFake implements ConsultaIdentidade {

    private final Map<Papel, Map<Cpf, Cadastro>> cadastros = new HashMap<>();
    final List<Papel> consultas = new ArrayList<>();
    private boolean indisponivel;

    IdentidadesFake com(Papel papel, Cpf cpf, Cadastro cadastro) {
        cadastros.computeIfAbsent(papel, p -> new HashMap<>()).put(cpf, cadastro);
        return this;
    }

    void ficarIndisponivel() {
        indisponivel = true;
    }

    @Override
    public Optional<Cadastro> buscar(Papel papel, Cpf cpf) {
        consultas.add(papel);
        if (indisponivel) {
            throw new ConsultaIdentidadeException("OS Service respondeu com status 503.");
        }
        return Optional.ofNullable(cadastros.getOrDefault(papel, Map.of()).get(cpf));
    }
}
