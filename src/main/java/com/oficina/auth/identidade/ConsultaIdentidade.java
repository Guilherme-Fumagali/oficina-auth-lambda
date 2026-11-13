package com.oficina.auth.identidade;

import com.oficina.auth.cpf.Cpf;

import java.util.Optional;

public interface ConsultaIdentidade {

    Optional<Cadastro> buscar(Papel papel, Cpf cpf);
}
