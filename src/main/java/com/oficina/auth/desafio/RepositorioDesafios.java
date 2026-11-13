package com.oficina.auth.desafio;

import java.util.Optional;

public interface RepositorioDesafios {

    void salvar(Desafio desafio);

    Optional<Desafio> registrarTentativa(String id);

    boolean remover(String id);
}
