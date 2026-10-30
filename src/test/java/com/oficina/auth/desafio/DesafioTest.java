package com.oficina.auth.desafio;

import com.oficina.auth.cliente.Cliente;
import com.oficina.auth.cliente.StatusCliente;
import com.oficina.auth.cpf.Cpf;
import com.oficina.auth.funcionario.Funcionario;
import com.oficina.auth.identidade.Papel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DesafioTest {

    private static final Instant AGORA = Instant.parse("2026-10-29T12:00:00.750Z");
    private static final Cliente CLIENTE = new Cliente(UUID.randomUUID(), "João", StatusCliente.ATIVO);

    @Test
    void novoDesafioComecaSemTentativasEValeCincoMinutos() {
        var desafio = Desafio.novo("id", CLIENTE, Cpf.de("52998224725"), "hash", AGORA);

        assertThat(desafio.tentativas()).isZero();
        assertThat(desafio.expiraEm()).isEqualTo(Instant.parse("2026-10-29T12:05:00Z"));
        assertThat(desafio.papel()).isEqualTo(Papel.CLIENTE);
    }

    @Test
    void expiraSomenteDepoisDoPrazo() {
        var desafio = Desafio.novo("id", CLIENTE, Cpf.de("52998224725"), "hash", AGORA);

        assertThat(desafio.expirado(desafio.expiraEm().minusSeconds(1))).isFalse();
        assertThat(desafio.expirado(desafio.expiraEm())).isFalse();
        assertThat(desafio.expirado(desafio.expiraEm().plusMillis(1))).isTrue();
    }

    @Test
    void tentativasSeEsgotamNaTerceira() {
        var titular = new Funcionario(UUID.randomUUID(), "Maria", true);
        var agora = Instant.now();

        assertThat(new Desafio("id", titular, Cpf.de("52998224725"), "h", 2, agora).tentativasEsgotadas()).isFalse();
        assertThat(new Desafio("id", titular, Cpf.de("52998224725"), "h", 3, agora).tentativasEsgotadas()).isTrue();
        assertThat(new Desafio("id", titular, Cpf.de("52998224725"), "h", 3, agora).papel())
            .isEqualTo(Papel.FUNCIONARIO);
    }
}
