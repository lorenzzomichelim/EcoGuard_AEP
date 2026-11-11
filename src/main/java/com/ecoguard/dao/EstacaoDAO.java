package com.ecoguard.dao;

import com.ecoguard.util.ConexaoBanco;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class EstacaoDAO {
    public void listarEstacoes() {
        String sql = "SELECT id_estacao, nome, area_reserva, latitude, longitude FROM TB_ESTACAO ORDER BY id_estacao";
        try (Connection conexao = ConexaoBanco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {
            boolean encontrou = false;
            while (resultado.next()) {
                encontrou = true;
                System.out.println("--------------------");
                System.out.println("ID: " + resultado.getInt("id_estacao"));
                System.out.println("Nome: " + resultado.getString("nome"));
                System.out.println("Area/reserva: " + resultado.getString("area_reserva"));
                System.out.println("Latitude: " + resultado.getObject("latitude"));
                System.out.println("Longitude: " + resultado.getObject("longitude"));
            }
            if (!encontrou) System.out.println("Nenhuma estacao cadastrada.");
        } catch (SQLException e) {
            System.out.println("Erro ao listar estacoes: " + e.getMessage());
        }
    }
}
