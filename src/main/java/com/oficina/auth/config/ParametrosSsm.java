package com.oficina.auth.config;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersRequest;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ParametrosSsm implements LeitorParametros {

    private final Supplier<SsmClient> clientes;
    private final String prefixo;

    public ParametrosSsm(Supplier<SsmClient> clientes, String prefixo) {
        this.clientes = clientes;
        this.prefixo = prefixo;
    }

    @Override
    public Map<String, String> ler(Collection<String> nomes) {
        var completos = nomes.stream().map(nome -> prefixo + "/" + nome).toList();
        try (var ssm = clientes.get()) {
            var resposta = ssm.getParameters(GetParametersRequest.builder()
                .names(completos)
                .withDecryption(true)
                .build());
            if (!resposta.invalidParameters().isEmpty()) {
                throw new IllegalStateException(
                    "Parâmetros ausentes no SSM: " + String.join(", ", resposta.invalidParameters()));
            }
            return resposta.parameters().stream()
                .collect(Collectors.toMap(p -> p.name().substring(prefixo.length() + 1), Parameter::value));
        }
    }
}
