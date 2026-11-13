package com.oficina.auth.handler;

import com.oficina.auth.desafio.Desafio;
import com.oficina.auth.desafio.RepositorioDesafios;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class DesafiosEmMemoria implements RepositorioDesafios {

    private final Map<String, Desafio> itens = new HashMap<>();
    private RuntimeException falha;
    private boolean outraRequisicaoRemoveAntes;

    void falharCom(RuntimeException falha) {
        this.falha = falha;
    }

    void outraRequisicaoRemoveAntes() {
        outraRequisicaoRemoveAntes = true;
    }

    Optional<Desafio> buscar(String id) {
        return Optional.ofNullable(itens.get(id));
    }

    int total() {
        return itens.size();
    }

    @Override
    public void salvar(Desafio desafio) {
        if (falha != null) {
            throw falha;
        }
        if (itens.putIfAbsent(desafio.id(), desafio) != null) {
            throw new IllegalStateException("Desafio repetido.");
        }
    }

    @Override
    public Optional<Desafio> registrarTentativa(String id) {
        if (falha != null) {
            throw falha;
        }
        var desafio = itens.get(id);
        if (desafio == null || desafio.tentativas() >= Desafio.MAXIMO_TENTATIVAS) {
            return Optional.empty();
        }
        var atualizado = new Desafio(desafio.id(), desafio.titular(), desafio.cpf(), desafio.codigoHash(),
            desafio.tentativas() + 1, desafio.expiraEm());
        itens.put(id, atualizado);
        return Optional.of(atualizado);
    }

    @Override
    public boolean remover(String id) {
        var existia = itens.remove(id) != null;
        if (outraRequisicaoRemoveAntes) {
            outraRequisicaoRemoveAntes = false;
            return false;
        }
        return existia;
    }
}
