package com.oficina.auth.config;

import com.oficina.auth.LocalStackCompartilhado;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParametrosSsmTest {

    private static final String PREFIXO = "/oficina/teste";

    private final ParametrosSsm parametros =
        new ParametrosSsm(() -> LocalStackCompartilhado.cliente(SsmClient.builder()), PREFIXO);

    @BeforeAll
    static void publicarParametros() {
        try (SsmClient ssm = LocalStackCompartilhado.cliente(SsmClient.builder())) {
            publicar(ssm, "jwt-secret", "segredo-jwt-cifrado", ParameterType.SECURE_STRING);
            publicar(ssm, "nlb-dns", "nlb-interno.elb.us-east-1.amazonaws.com", ParameterType.STRING);
        }
    }

    @Test
    void leParametrosSimplesECifradosPeloNomeCurto() {
        var valores = parametros.ler(List.of("jwt-secret", "nlb-dns"));

        assertThat(valores).isEqualTo(Map.of(
            "jwt-secret", "segredo-jwt-cifrado",
            "nlb-dns", "nlb-interno.elb.us-east-1.amazonaws.com"));
    }

    @Test
    void parametroInexistenteInterrompeACargaComONome() {
        assertThatThrownBy(() -> parametros.ler(List.of("jwt-secret", "otp-segredo")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(PREFIXO + "/otp-segredo");
    }

    private static void publicar(SsmClient ssm, String nome, String valor, ParameterType tipo) {
        ssm.putParameter(PutParameterRequest.builder()
            .name(PREFIXO + "/" + nome)
            .value(valor)
            .type(tipo)
            .overwrite(true)
            .build());
    }
}
