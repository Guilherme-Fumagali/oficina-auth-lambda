package com.oficina.auth.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguracaoTest {

    private final List<String> consultados = new ArrayList<>();
    private final Map<String, String> ssm = new HashMap<>(Map.of(
        "jwt-secret", "segredo-jwt-do-ssm",
        "chave-interna", "chave-do-ssm",
        "nlb-dns", "nlb-interno.elb.us-east-1.amazonaws.com"));

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
    void autenticacaoConsultaNoSsmApenasOQueFaltaNoAmbiente() {
        var ambiente = Map.of("JWT_SECRET", "segredo-local", "CHAVE_INTERNA", " ");

        var config = ConfiguracaoAutenticacao.de(new FonteConfiguracao(ambiente, leitor));

        assertThat(consultados).containsExactlyInAnyOrder("chave-interna", "nlb-dns");
        assertThat(config.jwtSecret()).isEqualTo("segredo-local");
        assertThat(config.chaveInterna()).isEqualTo("chave-do-ssm");
    }

    @Test
    void urlDoOsServiceApontaParaOListenerDoNlb() {
        var config = ConfiguracaoAutenticacao.de(new FonteConfiguracao(Map.of(), leitor));

        assertThat(config.urlOsService()).isEqualTo("http://nlb-interno.elb.us-east-1.amazonaws.com");
    }

    @Test
    void falhaDoSsmInterrompeACarga() {
        LeitorParametros ausente = nomes -> {
            throw new IllegalStateException("Parâmetros ausentes no SSM: " + String.join(", ", nomes));
        };

        assertThatThrownBy(() -> ConfiguracaoAutenticacao.de(new FonteConfiguracao(Map.of(), ausente)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("jwt-secret");
    }
}
