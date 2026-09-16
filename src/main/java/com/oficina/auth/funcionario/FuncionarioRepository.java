package com.oficina.auth.funcionario;

import com.oficina.auth.banco.ConexaoJdbc;
import com.oficina.auth.banco.ConsultaException;
import com.oficina.auth.cpf.Cpf;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class FuncionarioRepository {

    private static final String SQL = """
        SELECT id, nome, status
          FROM funcionarios
         WHERE cpf = ?
        """;

    private final ConexaoJdbc conexao;

    public FuncionarioRepository(ConexaoJdbc conexao) {
        this.conexao = conexao;
    }

    public Optional<Funcionario> buscarPorCpf(Cpf cpf) {
        try (var ps = conexao.obter().prepareStatement(SQL)) {
            ps.setString(1, cpf.valor());
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Funcionario(
                    UUID.fromString(rs.getString("id")),
                    rs.getString("nome"),
                    "ATIVO".equals(rs.getString("status"))));
            }
        } catch (SQLException e) {
            throw new ConsultaException("Falha ao consultar funcionário por CPF.", e);
        }
    }
}
