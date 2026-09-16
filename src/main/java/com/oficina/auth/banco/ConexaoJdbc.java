package com.oficina.auth.banco;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class ConexaoJdbc implements AutoCloseable {

    private final String jdbcUrl;
    private final Properties propriedades;
    private Connection conexao;

    public ConexaoJdbc(String jdbcUrl, String usuario, String senha) {
        this.jdbcUrl = jdbcUrl;
        this.propriedades = new Properties();
        propriedades.setProperty("user", usuario);
        propriedades.setProperty("password", senha);
        propriedades.setProperty("connectTimeout", "3");
        propriedades.setProperty("socketTimeout", "5");
        propriedades.setProperty("ApplicationName", "oficina-auth-lambda");
    }

    public Connection obter() throws SQLException {
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
}
