package com.oficina.auth.config;

import software.amazon.awssdk.services.ssm.SsmClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

final class FonteConfiguracao {

    private final Map<String, String> ambiente;
    private final LeitorParametros parametrosSsm;

    FonteConfiguracao(Map<String, String> ambiente, LeitorParametros parametrosSsm) {
        this.ambiente = ambiente;
        this.parametrosSsm = parametrosSsm;
    }

    static FonteConfiguracao doSistema() {
        var ambiente = System.getenv();
        var prefixo = ambiente.getOrDefault("SSM_PREFIX", "/oficina/prod");
        return new FonteConfiguracao(ambiente, new ParametrosSsm(SsmClient::create, prefixo));
    }

    Map<String, String> parametros(Map<String, String> variavelPorParametro) {
        var valores = new HashMap<String, String>();
        var ausentes = new ArrayList<String>();
        variavelPorParametro.forEach((parametro, variavel) -> {
            var valor = ambiente.get(variavel);
            if (valor == null || valor.isBlank()) {
                ausentes.add(parametro);
            } else {
                valores.put(parametro, valor);
            }
        });
        if (!ausentes.isEmpty()) {
            valores.putAll(parametrosSsm.ler(ausentes));
        }
        return valores;
    }

    String variavel(String nome) {
        var valor = ambiente.get(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variável de ambiente " + nome + " não definida.");
        }
        return valor;
    }
}
