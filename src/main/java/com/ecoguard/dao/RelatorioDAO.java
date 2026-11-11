package com.ecoguard.dao;

import com.ecoguard.util.ConexaoBanco;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class RelatorioDAO {

    public void listarLeituras() {
        String sql = "SELECT l.id_leitura, s.nome, l.valor, " +
                     "l.unidade_medida, l.data_hora " +
                     "FROM TB_LEITURA l " +
                     "JOIN TB_SENSOR s ON l.id_sensor = s.id_sensor " +
                     "ORDER BY l.id_leitura DESC";

        try (Connection conexao = ConexaoBanco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {

            System.out.println("\n=== HISTORICO DE LEITURAS ===");

            boolean encontrou = false;
            while (resultado.next()) {
                encontrou = true;
                System.out.println("--------------------");
                System.out.println("ID: " + resultado.getInt("id_leitura"));
                System.out.println("Sensor: " + resultado.getString("nome"));
                System.out.println("Valor: " + resultado.getDouble("valor") + " " +
                                   resultado.getString("unidade_medida"));
                System.out.println("Data: " + resultado.getTimestamp("data_hora"));
            }

            if (!encontrou) System.out.println("Nenhuma leitura encontrada.");
        } catch (SQLException e) {
            System.out.println("Erro ao listar leituras: " + e.getMessage());
        }
    }

    public void listarAlertas() {
        listarAlertas(false);
    }

    public void listarAlertasAbertos() {
        listarAlertas(true);
    }

    private void listarAlertas(boolean somenteAbertos) {
        String sql = "SELECT a.id_alerta, s.nome, s.tipo_sensor, l.valor, l.unidade_medida, u.nome AS responsavel, " +
                     "a.nivel_criticidade, a.status, a.data_hora " +
                     "FROM TB_ALERTA a " +
                     "JOIN TB_LEITURA l ON a.id_leitura = l.id_leitura " +
                     "JOIN TB_SENSOR s ON l.id_sensor = s.id_sensor " +
                     "LEFT JOIN TB_USUARIO u ON a.id_usuario_resp = u.id_usuario " +
                     "WHERE (? = FALSE OR a.status = 'ABERTO') " +
                     "ORDER BY a.id_alerta DESC";

        try (Connection conexao = ConexaoBanco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setBoolean(1, somenteAbertos);
            try (ResultSet resultado = comando.executeQuery()) {
                System.out.println(somenteAbertos ? "\n=== ALERTAS ABERTOS ===" : "\n=== ALERTAS ===");
                boolean encontrou = false;
    
                while (resultado.next()) {
                    System.out.println("--------------------");
                    encontrou = true;
                    System.out.println("ID: " + resultado.getInt("id_alerta"));
                    System.out.println("Sensor: " + resultado.getString("nome"));
                    System.out.println("Tipo: " + resultado.getString("tipo_sensor"));
                    System.out.println("Valor: " + resultado.getDouble("valor") + " " + resultado.getString("unidade_medida"));
                    String responsavel = resultado.getString("responsavel");
                    System.out.println("Responsavel: " + (responsavel == null ? "Nao atribuido" : responsavel));
                    System.out.println("Nivel: " + resultado.getString("nivel_criticidade"));
                    System.out.println("Status: " + resultado.getString("status"));
                    System.out.println("Data: " + resultado.getTimestamp("data_hora"));
                }
    
                if (!encontrou) System.out.println("Nenhum alerta encontrado.");
            }
        } catch (SQLException e) {
            System.out.println("Erro ao listar alertas: " + e.getMessage());
        }
    }
}
