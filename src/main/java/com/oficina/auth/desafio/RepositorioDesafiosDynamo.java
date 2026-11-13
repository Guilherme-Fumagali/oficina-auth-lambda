package com.oficina.auth.desafio;

import com.oficina.auth.cliente.Cliente;
import com.oficina.auth.cliente.StatusCliente;
import com.oficina.auth.cpf.Cpf;
import com.oficina.auth.funcionario.Funcionario;
import com.oficina.auth.identidade.Identidade;
import com.oficina.auth.identidade.Papel;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class RepositorioDesafiosDynamo implements RepositorioDesafios {

    private final DynamoDbClient dynamo;
    private final String tabela;

    public RepositorioDesafiosDynamo(DynamoDbClient dynamo, String tabela) {
        this.dynamo = dynamo;
        this.tabela = tabela;
    }

    @Override
    public void salvar(Desafio desafio) {
        dynamo.putItem(PutItemRequest.builder()
            .tableName(tabela)
            .item(item(desafio))
            .conditionExpression("attribute_not_exists(desafioId)")
            .build());
    }

    @Override
    public Optional<Desafio> registrarTentativa(String id) {
        try {
            var resposta = dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(tabela)
                .key(chave(id))
                .updateExpression("ADD tentativas :uma")
                .conditionExpression("attribute_exists(desafioId) AND tentativas < :maximo")
                .expressionAttributeValues(Map.of(
                    ":uma", numero(1),
                    ":maximo", numero(Desafio.MAXIMO_TENTATIVAS)))
                .returnValues(ReturnValue.ALL_NEW)
                .build());
            return Optional.of(desafio(resposta.attributes()));
        } catch (ConditionalCheckFailedException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean remover(String id) {
        var resposta = dynamo.deleteItem(DeleteItemRequest.builder()
            .tableName(tabela)
            .key(chave(id))
            .returnValues(ReturnValue.ALL_OLD)
            .build());
        return resposta.hasAttributes() && !resposta.attributes().isEmpty();
    }

    private static Map<String, AttributeValue> chave(String id) {
        return Map.of("desafioId", texto(id));
    }

    private static Map<String, AttributeValue> item(Desafio desafio) {
        return Map.of(
            "desafioId", texto(desafio.id()),
            "sub", texto(desafio.titular().id().toString()),
            "role", texto(desafio.papel().name()),
            "nome", texto(desafio.titular().nome()),
            "cpf", texto(desafio.cpf().valor()),
            "codigoHash", texto(desafio.codigoHash()),
            "tentativas", numero(desafio.tentativas()),
            "expiraEm", numero(desafio.expiraEm().getEpochSecond()));
    }

    private static Desafio desafio(Map<String, AttributeValue> item) {
        return new Desafio(
            item.get("desafioId").s(),
            titular(Papel.valueOf(item.get("role").s()), UUID.fromString(item.get("sub").s()), item.get("nome").s()),
            Cpf.de(item.get("cpf").s()),
            item.get("codigoHash").s(),
            Integer.parseInt(item.get("tentativas").n()),
            Instant.ofEpochSecond(Long.parseLong(item.get("expiraEm").n())));
    }

    private static Identidade titular(Papel papel, UUID id, String nome) {
        return papel == Papel.FUNCIONARIO
            ? new Funcionario(id, nome, true)
            : new Cliente(id, nome, StatusCliente.ATIVO);
    }

    private static AttributeValue texto(String valor) {
        return AttributeValue.builder().s(valor).build();
    }

    private static AttributeValue numero(long valor) {
        return AttributeValue.builder().n(Long.toString(valor)).build();
    }
}
