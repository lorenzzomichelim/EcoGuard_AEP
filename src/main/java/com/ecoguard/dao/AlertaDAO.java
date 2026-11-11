package com.ecoguard.dao;

import com.ecoguard.model.Usuario;
import com.ecoguard.util.ConexaoBanco;
import com.ecoguard.util.Validacao;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AlertaDAO {
    public void encerrarAlerta(int idAlerta, Usuario usuario) {
        if (usuario == null || (!"ADMINISTRADOR".equals(usuario.getPerfil())
                && !"ANALISTA".equals(usuario.getPerfil()))) {
            System.out.println("Acesso negado.");
            return;
        }
        Validacao.idPositivo(idAlerta);
        Validacao.idPositivo(usuario.getId());
        String consulta = "SELECT status FROM TB_ALERTA WHERE id_alerta = ? FOR UPDATE";
        String atualizar = "UPDATE TB_ALERTA SET status = 'ENCERRADO', id_usuario_resp = ? WHERE id_alerta = ?";
        try (Connection conexao = ConexaoBanco.conectar()) {
            conexao.setAutoCommit(false);
            try {
                try (PreparedStatement comando = conexao.prepareStatement(consulta)) {
                    comando.setInt(1, idAlerta);
                    try (ResultSet resultado = comando.executeQuery()) {
                        if (!resultado.next()) {
                            System.out.println("Alerta nao encontrado.");
                            conexao.rollback();
                            return;
                        }
                        if (!"ABERTO".equals(resultado.getString("status"))) {
                            System.out.println("Alerta ja encerrado ou indisponivel para encerramento.");
                            conexao.rollback();
                            return;
                        }
                    }
                }
                try (PreparedStatement comando = conexao.prepareStatement(atualizar)) {
                    comando.setInt(1, usuario.getId());
                    comando.setInt(2, idAlerta);
                    comando.executeUpdate();
                }
                conexao.commit();
                System.out.println("Alerta encerrado com sucesso! Responsavel: " + usuario.getNome());
            } catch (SQLException e) {
                conexao.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.out.println("Erro ao encerrar alerta: " + e.getMessage());
        }
    }
}
