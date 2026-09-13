package com.oficina.auth.cliente;

import com.oficina.auth.cpf.Cpf;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

public class ClienteRepository implements AutoCloseable {

    private static final String SQL = """
        SELECT id, nome, status
          FROM clientes
         WHERE cpf_cnpj = ?
        """;

    private final String jdbcUrl;
    private final Properties propriedades;
    private Connection conexao;

    public ClienteRepository(String jdbcUrl, String usuario, String senha) {
        this.jdbcUrl = jdbcUrl;
        this.propriedades = new Properties();
        propriedades.setProperty("user", usuario);
        propriedades.setProperty("password", senha);
        propriedades.setProperty("connectTimeout", "3");
        propriedades.setProperty("socketTimeout", "5");
        propriedades.setProperty("ApplicationName", "oficina-auth-lambda");
    }

    public Optional<Cliente> buscarPorCpf(Cpf cpf) {
        try (var ps = conexao().prepareStatement(SQL)) {
            ps.setString(1, cpf.valor());
            return extrair(ps);
        } catch (SQLException e) {
            throw new ConsultaClienteException("Falha ao consultar cliente por CPF.", e);
        }
    }

    private Optional<Cliente> extrair(PreparedStatement ps) throws SQLException {
        try (var rs = ps.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new Cliente(
                UUID.fromString(rs.getString("id")),
                rs.getString("nome"),
                StatusCliente.de(rs.getString("status"))));
        }
    }

    private Connection conexao() throws SQLException {
        if (conexao == null || conexao.isClosed()) {
            conexao = DriverManager.getConnection(jdbcUrl, propriedades);
        }
        return conexao;
    }

    @Override
    public void close() throws SQLException {
        if (conexao != null && !conexao.isClosed()) {
            conexao.close();
        }
    }

    public static class ConsultaClienteException extends RuntimeException {
        public ConsultaClienteException(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }
}
