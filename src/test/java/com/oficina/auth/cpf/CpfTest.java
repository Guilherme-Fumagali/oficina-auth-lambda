package com.oficina.auth.cpf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpfTest {

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25", "52998224725"})
    void deveAceitarCpfValidoComOuSemMascara(String entrada) {
        assertThat(Cpf.de(entrada).valor()).isEqualTo("52998224725");
    }

    @ParameterizedTest
    @ValueSource(strings = {"000.000.000-00", "11111111111", "123.456.789-00"})
    void deveRejeitarCpfInvalido(String entrada) {
        assertThatThrownBy(() -> Cpf.de(entrada)).isInstanceOf(CpfInvalidoException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123", "5299822472", "529982247251", "abcdefghijk"})
    void deveRejeitarEntradaComTamanhoErrado(String entrada) {
        assertThatThrownBy(() -> Cpf.de(entrada))
            .isInstanceOf(CpfInvalidoException.class)
            .hasMessageContaining("11 dígitos");
    }

    @Test
    void deveRejeitarCnpjMesmoQueValido() {
        // A Lambda autentica pessoa física por CPF; CNPJ tem 14 dígitos e não passa.
        assertThatThrownBy(() -> Cpf.de("11.222.333/0001-81"))
            .isInstanceOf(CpfInvalidoException.class);
    }

    @Test
    void deveMascararParaLogExpondoApenasOsTresUltimosDigitos() {
        var cpf = Cpf.de("529.982.247-25");

        assertThat(cpf.mascarado()).isEqualTo("***725");
        assertThat(cpf.mascarado()).doesNotContain("52998224");
        assertThat(cpf.toString()).isEqualTo("***725");
    }
}
