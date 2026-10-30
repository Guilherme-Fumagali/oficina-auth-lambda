package com.oficina.auth.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguracaoTest {

    private static final Map<String, String> AMBIENTE_DA_FUNCAO = Map.of(
        "TABELA_DESAFIOS", "oficina-auth-desafios-staging",
        "REMETENTE_EMAIL", "nao-responda@oficina.example.com");

    private final List<String> consultados = new ArrayList<>();
    private final Map<String, String> ssm = Map.of(
        "jwt-secret", "segredo-jwt-do-ssm",
        "chave-interna", "chave-do-ssm",
        "otp-segredo", "segredo-otp-do-ssm",
        "nlb-dns", "nlb-interno.elb.us-east-1.amazonaws.com");

    private final LeitorParametros leitor = nomes -> {
        consultados.addAll(nomes);
        var valores = new HashMap<String, String>();
        nomes.forEach(nome -> valores.put(nome, ssm.get(nome)));
        return valores;
    };

    @Test
    void autorizadorLeSomenteOSegredoDoJwt() {
        var config = ConfiguracaoAutorizador.de(new FonteConfiguracao(Map.of(), leitor));

        assertThat(config.jwtSecret()).isEqualTo("segredo-jwt-do-ssm");
        assertThat(consultados).containsExactly("jwt-secret");
    }

    @Test
    void variavelDeAmbienteDispensaOSsm() {
        LeitorParametros semSsm = nomes -> {
            throw new AssertionError("SSM não deveria ser consultado: " + nomes);
        };

        var config = ConfiguracaoAutorizador.de(
            new FonteConfiguracao(Map.of("JWT_SECRET", "segredo-local"), semSsm));

        assertThat(config.jwtSecret()).isEqualTo("segredo-local");
    }

    @Test
    void autenticacaoLeOsQuatroParametrosEAsVariaveisDaFuncao() {
        var config = ConfiguracaoAutenticacao.de(new FonteConfiguracao(AMBIENTE_DA_FUNCAO, leitor));

        assertThat(consultados).containsExactlyInAnyOrder("jwt-secret", "chave-interna", "otp-segredo", "nlb-dns");
        assertThat(config.jwtSecret()).isEqualTo("segredo-jwt-do-ssm");
        assertThat(config.chaveInterna()).isEqualTo("chave-do-ssm");
        assertThat(config.otpSegredo()).isEqualTo("segredo-otp-do-ssm");
        assertThat(config.urlOsService()).isEqualTo("http://nlb-interno.elb.us-east-1.amazonaws.com");
        assertThat(config.tabelaDesafios()).isEqualTo("oficina-auth-desafios-staging");
        assertThat(config.remetenteEmail()).isEqualTo("nao-responda@oficina.example.com");
    }

    @Test
    void autenticacaoConsultaNoSsmApenasOQueFaltaNoAmbiente() {
        var ambiente = new HashMap<>(AMBIENTE_DA_FUNCAO);
        ambiente.put("JWT_SECRET", "segredo-local");
        ambiente.put("OTP_SEGREDO", "segredo-otp-local");
        ambiente.put("NLB_DNS", "localhost:8080");
        ambiente.put("CHAVE_INTERNA", " ");

        var config = ConfiguracaoAutenticacao.de(new FonteConfiguracao(ambiente, leitor));

        assertThat(consultados).containsExactly("chave-interna");
        assertThat(config.jwtSecret()).isEqualTo("segredo-local");
        assertThat(config.otpSegredo()).isEqualTo("segredo-otp-local");
        assertThat(config.urlOsService()).isEqualTo("http://localhost:8080");
        assertThat(config.chaveInterna()).isEqualTo("chave-do-ssm");
    }

    @Test
    void tabelaERemetenteSaoObrigatorios() {
        assertThatThrownBy(() -> ConfiguracaoAutenticacao.de(new FonteConfiguracao(
                Map.of("REMETENTE_EMAIL", "nao-responda@oficina.example.com"), leitor)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TABELA_DESAFIOS");
        assertThatThrownBy(() -> ConfiguracaoAutenticacao.de(new FonteConfiguracao(
                Map.of("TABELA_DESAFIOS", "oficina-auth-desafios-staging", "REMETENTE_EMAIL", ""), leitor)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("REMETENTE_EMAIL");
        assertThat(consultados).isEmpty();
    }

    @Test
    void falhaDoSsmInterrompeACarga() {
        LeitorParametros ausente = nomes -> {
            throw new IllegalStateException("Parâmetros ausentes no SSM: " + String.join(", ", nomes));
        };

        assertThatThrownBy(() -> ConfiguracaoAutenticacao.de(new FonteConfiguracao(AMBIENTE_DA_FUNCAO, ausente)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("otp-segredo");
    }
}
