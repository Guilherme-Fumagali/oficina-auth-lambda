package com.oficina.auth.desafio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashCodigoTest {

    private static final String SEGREDO = "segredo-otp-de-teste-com-pelo-menos-32-bytes";
    private static final String DESAFIO = "6f1c0e3e-5a4b-4c2d-9e8f-0123456789ab";

    private final HashCodigo hash = new HashCodigo(SEGREDO);

    @Test
    void reproduzOVetorCalculadoForaDaAplicacao() {
        assertThat(hash.calcular(DESAFIO, "123456"))
            .isEqualTo("9f497ae374060519e6d4c9596dd1469678f39f68e6671233349573b0f811fb03");
        assertThat(hash.calcular(DESAFIO, "000042"))
            .isEqualTo("fc9a20a11795e3401c3864acbd7eabac46d070146eabce8b78222d2b1dd73430");
    }

    @Test
    void confereSomenteOCodigoDoProprioDesafio() {
        var esperado = hash.calcular(DESAFIO, "123456");

        assertThat(hash.confere(DESAFIO, "123456", esperado)).isTrue();
        assertThat(hash.confere(DESAFIO, "123457", esperado)).isFalse();
        assertThat(hash.confere("outro-desafio", "123456", esperado)).isFalse();
    }

    @Test
    void outroSegredoProduzOutroHash() {
        var outro = new HashCodigo("outro-segredo-otp-tambem-com-32-bytes-ou-mais");

        assertThat(outro.calcular(DESAFIO, "123456")).isNotEqualTo(hash.calcular(DESAFIO, "123456"));
    }

    @Test
    void exigeSegredoComPeloMenos32Bytes() {
        assertThatThrownBy(() -> new HashCodigo("curto"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new HashCodigo(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
