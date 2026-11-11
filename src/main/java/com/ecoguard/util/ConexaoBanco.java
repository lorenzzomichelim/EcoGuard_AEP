
package com.ecoguard.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConexaoBanco {

    public static Connection conectar() throws SQLException {
        // A propriedade e local ao processo; permite isolar testes sem alterar o banco padrao.
        String url = System.getProperty("ecoguard.db.url", "jdbc:postgresql://localhost:5432/ecoguard");
        String usuario = "postgres";
        String senha = System.getenv("ECOGUARD_DB_PASSWORD");

        if (senha == null || senha.isEmpty()) {
            throw new SQLException("Configure a variavel ECOGUARD_DB_PASSWORD.");
        }

        return DriverManager.getConnection(url, usuario, senha);
    }
}
