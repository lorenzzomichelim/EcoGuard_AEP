package com.ecoguard.dao;

import com.ecoguard.util.ConexaoBanco;
import com.ecoguard.util.Validacao;
import com.ecoguard.model.*;
import java.sql.*;

public class LeituraDAO {
    public void registrarLeitura(int idSensor, double valor) {
        Validacao.idPositivo(idSensor);
        Validacao.numeroFinito(valor);
        String consulta = "SELECT nome, tipo_sensor, status FROM TB_SENSOR WHERE id_sensor = ? FOR SHARE";
        String inserir = "INSERT INTO TB_LEITURA (id_sensor, valor, unidade_medida) VALUES (?, ?, ?) RETURNING id_leitura, valor";
        String alerta = "INSERT INTO TB_ALERTA (id_leitura, nivel_criticidade, status) VALUES (?, 'CRITICO', 'ABERTO')";
        try (Connection conexao = ConexaoBanco.conectar()) {
            conexao.setAutoCommit(false);
            try {
                String nome;
                String tipo;
                try (PreparedStatement comando = conexao.prepareStatement(consulta)) {
                    comando.setInt(1, idSensor);
                    try (ResultSet resultado = comando.executeQuery()) {
                        if (!resultado.next()) {
                            System.out.println("Sensor nao encontrado.");
                            conexao.rollback();
                            return;
                        }
                        if (!"ATIVO".equals(resultado.getString("status"))) {
                            System.out.println("Sensor inativo.");
                            conexao.rollback();
                            return;
                        }
                        nome = resultado.getString("nome");
                        tipo = resultado.getString("tipo_sensor");
                    }
                }
                Validacao.leitura(tipo, valor);
                DispositivoSensor sensor;
                String unidade;
                switch (tipo) {
                    case "TEMPERATURA":
                        sensor = new SensorTemperatura(idSensor, nome, valor);
                        unidade = "C";
                        break;
                    case "UMIDADE":
                        sensor = new SensorUmidade(idSensor, nome, valor);
                        unidade = "%";
                        break;
                    case "FUMACA":
                        sensor = new SensorFumaca(idSensor, nome, valor);
                        unidade = "ppm";
                        break;
                    default:
                        throw new IllegalArgumentException("Tipo de sensor invalido.");
                }
                int idLeitura;
                double valorRegistrado;
                try (PreparedStatement salvar = conexao.prepareStatement(inserir)) {
                    salvar.setInt(1, idSensor);
                    salvar.setDouble(2, valor);
                    salvar.setString(3, unidade);
                    try (ResultSet registro = salvar.executeQuery()) {
                        if (!registro.next()) throw new SQLException("A leitura nao retornou um ID.");
                        idLeitura = registro.getInt("id_leitura");
                        valorRegistrado = registro.getDouble("valor");
                    }
                }
                // A mesma chamada executa a regra da subclasse correspondente.
                // NUMERIC(8,2) arredonda a leitura. O alerta deve usar o valor persistido.
                Leitura leitura = new Leitura(idLeitura, valorRegistrado, unidade);
                sensor.adicionarLeitura(leitura);
                boolean critica = sensor.processarLeitura();
                if (critica) {
                    try (PreparedStatement salvarAlerta = conexao.prepareStatement(alerta)) {
                        salvarAlerta.setInt(1, idLeitura);
                        salvarAlerta.executeUpdate();
                    }
                }
                conexao.commit();
                System.out.println(critica ? "ALERTA CRITICO! Valor fora do limite simulado." : "Leitura normal.");
                System.out.println("Leitura registrada com sucesso!");
                System.out.println("ID da leitura: " + leitura.getId());
            } catch (SQLException | IllegalArgumentException e) {
                conexao.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.out.println("Erro ao registrar leitura: " + e.getMessage());
        }
    }
}
