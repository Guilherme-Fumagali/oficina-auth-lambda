package com.oficina.auth.notificacao;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MensagemCodigoTest {

    @Test
    void corpoTrazCodigoValidadeEApenasOPrimeiroNome() {
        var mensagem = MensagemCodigo.para("  Maria Aparecida Souza ", "007310", Duration.ofMinutes(5));

        assertThat(mensagem.assunto()).isEqualTo("Código de acesso — Oficina");
        assertThat(mensagem.corpo())
            .startsWith("Olá, Maria.")
            .contains("007310", "5 minutos", "uma única vez")
            .doesNotContain("Aparecida", "Souza");
    }

    @Test
    void semNomeUsaSaudacaoNeutra() {
        assertThat(MensagemCodigo.para(null, "123456", Duration.ofMinutes(5)).corpo()).startsWith("Olá.\n");
        assertThat(MensagemCodigo.para(" ", "123456", Duration.ofMinutes(5)).corpo()).startsWith("Olá.\n");
    }
}
