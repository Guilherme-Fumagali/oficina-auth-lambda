package com.oficina.auth.identidade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.auth.cliente.Cliente;
import com.oficina.auth.cliente.StatusCliente;
import com.oficina.auth.cpf.Cpf;
import com.oficina.auth.funcionario.Funcionario;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public class ConsultaIdentidadeHttp implements ConsultaIdentidade {

    public static final String CABECALHO_CHAVE = "X-Chave-Interna";
    static final Duration TEMPO_LIMITE = Duration.ofSeconds(2);

    private static final ObjectMapper JSON = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final HttpClient http;
    private final String urlBase;
    private final String chaveInterna;

    public ConsultaIdentidadeHttp(String urlBase, String chaveInterna) {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(TEMPO_LIMITE)
                .build(),
            urlBase, chaveInterna);
    }

    ConsultaIdentidadeHttp(HttpClient http, String urlBase, String chaveInterna) {
        this.http = http;
        this.urlBase = urlBase;
        this.chaveInterna = chaveInterna;
    }

    @Override
    public Optional<Cadastro> buscar(Papel papel, Cpf cpf) {
        var resposta = enviar(HttpRequest.newBuilder(URI.create(urlBase + caminho(papel) + cpf.valor()))
            .timeout(TEMPO_LIMITE)
            .header(CABECALHO_CHAVE, chaveInterna)
            .header("Accept", "application/json")
            .GET()
            .build());

        return switch (resposta.statusCode()) {
            case 200 -> Optional.of(converter(papel, resposta.body()));
            case 404 -> Optional.empty();
            default -> throw new ConsultaIdentidadeException(
                "OS Service respondeu com status " + resposta.statusCode() + ".");
        };
    }

    private static String caminho(Papel papel) {
        return papel == Papel.FUNCIONARIO
            ? "/internal/identidades/funcionarios/"
            : "/internal/identidades/clientes/";
    }

    private HttpResponse<String> enviar(HttpRequest requisicao) {
        try {
            return http.send(requisicao, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ConsultaIdentidadeException("Falha de comunicação com o OS Service.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConsultaIdentidadeException("Consulta ao OS Service interrompida.", e);
        }
    }

    private static Cadastro converter(Papel papel, String corpo) {
        IdentidadeResponse dados;
        try {
            dados = JSON.readValue(corpo, IdentidadeResponse.class);
        } catch (JsonProcessingException e) {
            throw new ConsultaIdentidadeException("Resposta do OS Service em formato inesperado.", e);
        }
        if (dados == null || dados.id() == null) {
            throw new ConsultaIdentidadeException("Resposta do OS Service sem identificador.");
        }

        var id = uuid(dados.id());
        Identidade identidade = papel == Papel.FUNCIONARIO
            ? new Funcionario(id, dados.nome(), "ATIVO".equals(dados.status()))
            : new Cliente(id, dados.nome(), StatusCliente.de(dados.status()));
        return new Cadastro(identidade, dados.email());
    }

    private static UUID uuid(String valor) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            throw new ConsultaIdentidadeException("Identificador devolvido pelo OS Service não é um UUID.", e);
        }
    }

    record IdentidadeResponse(String id, String nome, String email, String status) {}
}
