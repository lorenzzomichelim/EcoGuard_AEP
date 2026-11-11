package com.ecoguard.dao;

import com.ecoguard.util.ConexaoBanco;
import com.ecoguard.util.Validacao;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SensorDAO {

    public void listarSensores() {
        String sql = "SELECT id_sensor, id_estacao, nome, tipo_sensor, status FROM TB_SENSOR ORDER BY id_sensor";

        try (Connection conexao = ConexaoBanco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {

            boolean encontrou = false;
            while (resultado.next()) {
                encontrou = true;
                System.out.println("--------------------");
                System.out.println("ID: " + resultado.getInt("id_sensor"));
                System.out.println("Nome: " + resultado.getString("nome"));
                System.out.println("Estacao: " + resultado.getInt("id_estacao"));
                System.out.println("Tipo: " + resultado.getString("tipo_sensor"));
                System.out.println("Status: " + resultado.getString("status"));
            }

            if (!encontrou) System.out.println("Nenhum sensor cadastrado.");
        } catch (SQLException e) {
            System.out.println("Erro ao listar sensores: " + e.getMessage());
        }
    }

    public void cadastrarSensor(int idEstacao, String nome, String tipo, String status) {
        Validacao.idPositivo(idEstacao);
        nome = Validacao.textoObrigatorio(nome);
        tipo = Validacao.tipoSensor(tipo);
        status = Validacao.statusSensor(status);
        String sql = "INSERT INTO TB_SENSOR (id_estacao, nome, tipo_sensor, status, data_instalacao) VALUES (?, ?, ?, ?, CURRENT_DATE)";
        try (Connection conexao = ConexaoBanco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, idEstacao);
            comando.setString(2, nome);
            comando.setString(3, tipo);
            comando.setString(4, status);
            comando.executeUpdate();
            System.out.println("Sensor cadastrado com sucesso!");
        } catch (SQLException e) {
            if ("23503".equals(e.getSQLState())) {
                System.out.println("Estacao nao encontrada. Consulte as estacoes na opcao 9.");
            } else {
                System.out.println("Erro ao cadastrar sensor: " + e.getMessage());
            }
        }
    }

    public void atualizarSensor(int id, String nome, String status) {
        Validacao.idPositivo(id);
        nome = Validacao.textoObrigatorio(nome);
        status = Validacao.statusSensor(status);
        String sql = "UPDATE TB_SENSOR SET nome = ?, status = ? WHERE id_sensor = ?";
        try (Connection conexao = ConexaoBanco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, nome);
            comando.setString(2, status);
            comando.setInt(3, id);
            if (comando.executeUpdate() > 0) {
                System.out.println("Sensor atualizado com sucesso!");
            } else {
                System.out.println("Sensor nao encontrado.");
            }
        } catch (SQLException e) {
            System.out.println("Erro ao atualizar sensor: " + e.getMessage());
        }
    }

    public void excluirSensor(int id) {
        Validacao.idPositivo(id);
        String sql = "DELETE FROM TB_SENSOR WHERE id_sensor = ?";
        try (Connection conexao = ConexaoBanco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, id);
            if (comando.executeUpdate() > 0) {
                System.out.println("Sensor excluido com sucesso!");
            } else {
                System.out.println("Sensor nao encontrado.");
            }
        } catch (SQLException e) {
            System.out.println("Erro ao excluir sensor: " + e.getMessage());
        }
    }
}
