package com.oficina.auth.desafio;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class GeradorCodigoTest {

    private static final int AMOSTRA = 20_000;

    @Test
    void completaComZerosAEsquerda() {
        assertThat(new GeradorCodigo(fixo(42)).gerar()).isEqualTo("000042");
        assertThat(new GeradorCodigo(fixo(0)).gerar()).isEqualTo("000000");
    }

    @Test
    void maiorValorTemSeisDigitos() {
        assertThat(new GeradorCodigo(fixo(999_999)).gerar()).isEqualTo("999999");
    }

    @Test
    void sorteiaEntreUmMilhaoDeValores() {
        var limite = new AtomicInteger();
        new GeradorCodigo(new RandomGenerator() {
            @Override public long nextLong() { return 0; }
            @Override public int nextInt(int bound) {
                limite.set(bound);
                return 0;
            }
        }).gerar();

        assertThat(limite.get()).isEqualTo(1_000_000);
    }

    @Test
    void distribuiOsDigitosDeFormaUniforme() {
        var gerador = new GeradorCodigo();
        var codigos = IntStream.range(0, AMOSTRA).mapToObj(i -> gerador.gerar()).toList();

        assertThat(codigos).allMatch(codigo -> codigo.matches("\\d{6}"));
        assertThat(new HashSet<>(codigos)).hasSizeGreaterThan(AMOSTRA - 500);
        for (int posicao = 0; posicao < 6; posicao++) {
            var ocorrencias = new int[10];
            for (var codigo : codigos) {
                ocorrencias[codigo.charAt(posicao) - '0']++;
            }
            assertThat(IntStream.of(ocorrencias)).allSatisfy(total -> assertThat(total).isBetween(1_600, 2_400));
        }
    }

    private static RandomGenerator fixo(int valor) {
        return new RandomGenerator() {
            @Override public long nextLong() { return valor; }
            @Override public int nextInt(int bound) { return valor; }
        };
    }
}
