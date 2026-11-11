
package com.ecoguard.dao;

import com.ecoguard.util.ConexaoBanco;
import com.ecoguard.model.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

public class UsuarioDAO {

    public Usuario autenticar(String email, String senha) throws SQLException {
        try (Connection conexao = ConexaoBanco.conectar()) {
            return autenticar(conexao, email, senha);
        }
    }

    // Permite testar a mesma consulta com usuarios temporarios e rollback.
    // A conexao recebida pertence ao chamador e nao e fechada neste metodo.
    public Usuario autenticar(Connection conexao, String email, String senha) throws SQLException {
        String sql = "SELECT id_usuario, nome, email, perfil FROM TB_USUARIO " +
                     "WHERE email = ? AND senha_hash = ?";

        try (PreparedStatement comando = conexao.prepareStatement(sql)) {

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(senha.getBytes(StandardCharsets.UTF_8));

            StringBuilder senhaHash = new StringBuilder();

            for (byte b : hash) {
                senhaHash.append(String.format("%02x", b & 0xff));
            }

            comando.setString(1, email);
            comando.setString(2, senhaHash.toString());

            try (ResultSet resultado = comando.executeQuery()) {
                if (resultado.next()) {
                    return new Usuario(resultado.getInt("id_usuario"), resultado.getString("nome"),
                            resultado.getString("email"), resultado.getString("perfil"));
                }
            }

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel no Java.", e);
        }

        return null;
    }
}
