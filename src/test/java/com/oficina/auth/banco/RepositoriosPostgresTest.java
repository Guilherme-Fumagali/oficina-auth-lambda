package com.oficina.auth.banco;

import com.oficina.auth.cliente.ClienteRepository;
import com.oficina.auth.cliente.StatusCliente;
import com.oficina.auth.cpf.Cpf;
import com.oficina.auth.funcionario.FuncionarioRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RepositoriosPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withInitScript("schema-autenticacao.sql");

    private static ConexaoJdbc conexao;
    private static ClienteRepository clientes;
    private static FuncionarioRepository funcionarios;

    @BeforeAll
    static void conectar() {
        conexao = new ConexaoJdbc(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        clientes = new ClienteRepository(conexao);
        funcionarios = new FuncionarioRepository(conexao);
    }

    @AfterAll
    static void desconectar() throws Exception {
        conexao.close();
    }

    @Test
    void encontraClienteAtivoPeloCpfNormalizado() {
        var cliente = clientes.buscarPorCpf(Cpf.de("529.982.247-25")).orElseThrow();

        assertThat(cliente.id()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(cliente.nome()).isEqualTo("João Ativo");
        assertThat(cliente.podeAutenticar()).isTrue();
    }

    @Test
    void clienteBloqueadoNaoPodeAutenticar() {
        var cliente = clientes.buscarPorCpf(Cpf.de("11144477735")).orElseThrow();

        assertThat(cliente.status()).isEqualTo(StatusCliente.BLOQUEADO);
        assertThat(cliente.podeAutenticar()).isFalse();
    }

    @Test
    void cpfSemCadastroRetornaVazio() {
        assertThat(clientes.buscarPorCpf(Cpf.de("39053344705"))).isEmpty();
        assertThat(funcionarios.buscarPorCpf(Cpf.de("52998224725"))).isEmpty();
    }

    @Test
    void encontraFuncionarioAtivoEInativo() {
        assertThat(funcionarios.buscarPorCpf(Cpf.de("39053344705")).orElseThrow().podeAutenticar()).isTrue();
        assertThat(funcionarios.buscarPorCpf(Cpf.de("15350946056")).orElseThrow().podeAutenticar()).isFalse();
    }

    @Test
    void falhaDeConexaoViraConsultaException() {
        var inacessivel = new ClienteRepository(
            new ConexaoJdbc("jdbc:postgresql://127.0.0.1:1/inexistente", "x", "x"));

        assertThatThrownBy(() -> inacessivel.buscarPorCpf(Cpf.de("52998224725")))
            .isInstanceOf(ConsultaException.class);
    }
}
