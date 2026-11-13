package com.oficina.auth.desafio;

import com.oficina.auth.LocalStackCompartilhado;
import com.oficina.auth.cliente.Cliente;
import com.oficina.auth.cliente.StatusCliente;
import com.oficina.auth.cpf.Cpf;
import com.oficina.auth.funcionario.Funcionario;
import com.oficina.auth.identidade.Identidade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveSpecification;
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositorioDesafiosDynamoTest {

    private static final String TABELA = "oficina-auth-desafios-teste";
    private static final Cpf CPF = Cpf.de("52998224725");

    private static DynamoDbClient dynamo;
    private static RepositorioDesafiosDynamo repositorio;

    @BeforeAll
    static void criarTabela() {
        dynamo = LocalStackCompartilhado.cliente(DynamoDbClient.builder());
        dynamo.createTable(CreateTableRequest.builder()
            .tableName(TABELA)
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .attributeDefinitions(AttributeDefinition.builder()
                .attributeName("desafioId").attributeType(ScalarAttributeType.S).build())
            .keySchema(KeySchemaElement.builder().attributeName("desafioId").keyType(KeyType.HASH).build())
            .build());
        dynamo.waiter().waitUntilTableExists(r -> r.tableName(TABELA));
        dynamo.updateTimeToLive(UpdateTimeToLiveRequest.builder()
            .tableName(TABELA)
            .timeToLiveSpecification(TimeToLiveSpecification.builder()
                .attributeName("expiraEm").enabled(true).build())
            .build());
        repositorio = new RepositorioDesafiosDynamo(dynamo, TABELA);
    }

    @Test
    void tentativaRegistradaDevolveODesafioGravadoComOContador() {
        var desafio = novoDesafio(new Cliente(UUID.randomUUID(), "João Ativo", StatusCliente.ATIVO));
        repositorio.salvar(desafio);

        var lido = repositorio.registrarTentativa(desafio.id()).orElseThrow();

        assertThat(lido).isEqualTo(new Desafio(desafio.id(), desafio.titular(), CPF,
            desafio.codigoHash(), 1, desafio.expiraEm()));
    }

    @Test
    void funcionarioVoltaComPapelDeFuncionario() {
        var desafio = novoDesafio(new Funcionario(UUID.randomUUID(), "Maria Ativa", true));
        repositorio.salvar(desafio);

        assertThat(repositorio.registrarTentativa(desafio.id()).orElseThrow().titular())
            .isEqualTo(desafio.titular());
    }

    @Test
    void itemGuardaApenasOHashDoCodigoEOsAtributosDaSpec() {
        var desafio = novoDesafio(new Cliente(UUID.randomUUID(), "João Ativo", StatusCliente.ATIVO));
        repositorio.salvar(desafio);

        var item = lerItem(desafio.id()).orElseThrow();

        assertThat(item.keySet()).containsExactlyInAnyOrder(
            "desafioId", "sub", "role", "nome", "cpf", "codigoHash", "tentativas", "expiraEm");
        assertThat(item.get("role").s()).isEqualTo("CLIENTE");
        assertThat(item.get("sub").s()).isEqualTo(desafio.titular().id().toString());
        assertThat(item.get("codigoHash").s()).isEqualTo(desafio.codigoHash()).doesNotContain("123456");
        assertThat(item.get("tentativas").n()).isEqualTo("0");
        assertThat(item.get("expiraEm").n()).isEqualTo(Long.toString(desafio.expiraEm().getEpochSecond()));
    }

    @Test
    void permiteNoMaximoTresTentativas() {
        var desafio = novoDesafio(new Cliente(UUID.randomUUID(), "João Ativo", StatusCliente.ATIVO));
        repositorio.salvar(desafio);

        assertThat(repositorio.registrarTentativa(desafio.id()).orElseThrow().tentativas()).isEqualTo(1);
        assertThat(repositorio.registrarTentativa(desafio.id()).orElseThrow().tentativas()).isEqualTo(2);
        assertThat(repositorio.registrarTentativa(desafio.id()).orElseThrow().tentativasEsgotadas()).isTrue();
        assertThat(repositorio.registrarTentativa(desafio.id())).isEmpty();
        assertThat(lerItem(desafio.id()).orElseThrow().get("tentativas").n()).isEqualTo("3");
    }

    @Test
    void tentativasSimultaneasRespeitamOLimite() throws Exception {
        var desafio = novoDesafio(new Cliente(UUID.randomUUID(), "João Ativo", StatusCliente.ATIVO));
        repositorio.salvar(desafio);

        var tarefas = new ArrayList<Callable<Optional<Desafio>>>();
        for (int i = 0; i < 10; i++) {
            tarefas.add(() -> repositorio.registrarTentativa(desafio.id()));
        }
        long aceitas;
        try (var executor = Executors.newFixedThreadPool(10)) {
            aceitas = executor.invokeAll(tarefas).stream()
                .map(RepositorioDesafiosDynamoTest::resultado)
                .filter(Optional::isPresent)
                .count();
        }

        assertThat(aceitas).isEqualTo(Desafio.MAXIMO_TENTATIVAS);
    }

    @Test
    void desafioInexistenteNaoGanhaItemNovo() {
        var id = UUID.randomUUID().toString();

        assertThat(repositorio.registrarTentativa(id)).isEmpty();
        assertThat(lerItem(id)).isEmpty();
    }

    @Test
    void remocaoInformaSeODesafioAindaExistia() {
        var desafio = novoDesafio(new Cliente(UUID.randomUUID(), "João Ativo", StatusCliente.ATIVO));
        repositorio.salvar(desafio);

        assertThat(repositorio.remover(desafio.id())).isTrue();
        assertThat(repositorio.remover(desafio.id())).isFalse();
        assertThat(repositorio.registrarTentativa(desafio.id())).isEmpty();
    }

    @Test
    void naoSobrescreveDesafioComOMesmoId() {
        var desafio = novoDesafio(new Cliente(UUID.randomUUID(), "João Ativo", StatusCliente.ATIVO));
        repositorio.salvar(desafio);

        assertThatThrownBy(() -> repositorio.salvar(desafio))
            .isInstanceOf(ConditionalCheckFailedException.class);
    }

    private static Desafio novoDesafio(Identidade titular) {
        var id = UUID.randomUUID().toString();
        return Desafio.novo(id, titular, CPF, new HashCodigo("segredo-otp-de-teste-com-pelo-menos-32-bytes")
            .calcular(id, "123456"), Instant.now());
    }

    private static Optional<Map<String, AttributeValue>> lerItem(String id) {
        var resposta = dynamo.getItem(GetItemRequest.builder()
            .tableName(TABELA)
            .key(Map.of("desafioId", AttributeValue.builder().s(id).build()))
            .consistentRead(true)
            .build());
        return resposta.hasItem() && !resposta.item().isEmpty() ? Optional.of(resposta.item()) : Optional.empty();
    }

    private static Optional<Desafio> resultado(Future<Optional<Desafio>> futuro) {
        try {
            return futuro.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
