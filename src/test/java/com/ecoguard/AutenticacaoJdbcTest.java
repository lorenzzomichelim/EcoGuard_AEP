package com.ecoguard;

import com.ecoguard.dao.UsuarioDAO;
import com.ecoguard.model.*;
import com.ecoguard.util.ConexaoBanco;
import java.sql.*;
import java.util.UUID;

// Integracao real: fixtures apenas em pg_temp, sem sequencias ou escritas em public.
public class AutenticacaoJdbcTest {
    public static void main(String[] args) throws SQLException {
        try (Connection conexao = ConexaoBanco.conectar()) {
            conexao.setAutoCommit(false);
            try {
                try (Statement comando = conexao.createStatement()) {
                    comando.execute("SET LOCAL statement_timeout = '15s'");
                    comando.execute("SET LOCAL search_path = pg_temp, public");
                    // Sem INCLUDING DEFAULTS: nao utiliza as sequencias das tabelas reais.
                    comando.execute("CREATE TEMP TABLE tb_usuario (LIKE public.tb_usuario INCLUDING CONSTRAINTS) ON COMMIT DROP");
                    comando.execute("CREATE TEMP TABLE tb_leitura (LIKE public.tb_leitura INCLUDING CONSTRAINTS) ON COMMIT DROP");
                }
                verificarLogin(conexao);
                verificarArredondamento(conexao);
            } finally {
                conexao.rollback();
            }
            try (Statement comando = conexao.createStatement();
                 ResultSet resultado = comando.executeQuery("SELECT to_regclass('pg_temp.tb_usuario'), to_regclass('pg_temp.tb_leitura')")) {
                if (!resultado.next() || resultado.getString(1) != null || resultado.getString(2) != null) {
                    throw new AssertionError("Fixtures temporarias nao foram desfeitas.");
                }
            }
            conexao.rollback();
            System.out.println("ROLLBACK confirmado: tabelas temporarias removidas, nenhuma escrita em public ou consumo de sequencias.");
        }
    }

    private static void verificarLogin(Connection conexao) throws SQLException {
        UsuarioDAO dao = new UsuarioDAO();
        String[] perfis = {"ADMINISTRADOR", "ANALISTA", "BRIGADISTA"};
        String sql = "INSERT INTO pg_temp.tb_usuario (id_usuario, nome, email, senha_hash, perfil) "
                + "VALUES (?, ?, ?, encode(sha256(convert_to(?, 'UTF8')), 'hex'), ?)";
        for (int i = 0; i < perfis.length; i++) {
            String email = "fixture-" + i + "@invalid";
            String senha = UUID.randomUUID().toString();
            int id = i + 1;
            try (PreparedStatement comando = conexao.prepareStatement(sql)) {
                comando.setInt(1, id);
                comando.setString(2, "Usuario temporario");
                comando.setString(3, email);
                comando.setString(4, senha);
                comando.setString(5, perfis[i]);
                comando.executeUpdate();
            }
            // A mesma consulta e o mesmo hash Java usados pelo login da aplicacao.
            Usuario usuario = dao.autenticar(conexao, email, senha);
            if (usuario == null || usuario.getId() != id || !perfis[i].equals(usuario.getPerfil())) {
                throw new AssertionError("Login real de fixture falhou: " + perfis[i]);
            }
            if (dao.autenticar(conexao, email, senha + "incorreta") != null) {
                throw new AssertionError("Senha incorreta foi aceita.");
            }
        }
        if (dao.autenticar(conexao, "nao-cadastrado@invalid", UUID.randomUUID().toString()) != null
                || dao.autenticar(conexao, "' OR '1'='1", UUID.randomUUID().toString()) != null) {
            throw new AssertionError("Credencial inexistente ou entrada SQL foi aceita.");
        }
        System.out.println("Autenticacao JDBC real: tres perfis temporarios aprovados, tres senhas incorretas e duas entradas invalidas recusadas.");
        System.out.println("Fixtures com hash calculado pelo PostgreSQL e verificado pelo UsuarioDAO; senhas e hashes omitidos.");
    }

    private static void verificarArredondamento(Connection conexao) throws SQLException {
        double[] entradas = {54.999, 20.004, 149.999, 54.994, 20.006, 149.994};
        double[] esperados = {55, 20, 150, 54.99, 20.01, 149.99};
        String sql = "INSERT INTO pg_temp.tb_leitura (id_leitura, id_sensor, valor, unidade_medida) VALUES (?, 1, ?, ?) RETURNING valor";
        for (int i = 0; i < entradas.length; i++) {
            String unidade = i % 3 == 0 ? "C" : (i % 3 == 1 ? "%" : "ppm");
            try (PreparedStatement comando = conexao.prepareStatement(sql)) {
                comando.setInt(1, i + 1);
                comando.setDouble(2, entradas[i]);
                comando.setString(3, unidade);
                try (ResultSet resultado = comando.executeQuery()) {
                    if (!resultado.next()) throw new AssertionError("Valor nao retornado.");
                    double persistido = resultado.getDouble("valor");
                    if (Math.abs(persistido - esperados[i]) > 0.000001) {
                        throw new AssertionError("Arredondamento NUMERIC inesperado.");
                    }
                    DispositivoSensor sensor;
                    switch (i % 3) {
                        case 0: sensor = new SensorTemperatura(1, "Temporario", entradas[i]); break;
                        case 1: sensor = new SensorUmidade(1, "Temporario", entradas[i]); break;
                        default: sensor = new SensorFumaca(1, "Temporario", entradas[i]);
                    }
                    sensor.adicionarLeitura(new Leitura(i + 1, persistido, unidade));
                    if (sensor.processarLeitura() != (i < 3)) {
                        throw new AssertionError("Regra de alerta divergiu do valor retornado pelo banco.");
                    }
                }
            }
        }
        System.out.println("Seis casos JDBC NUMERIC(8,2): arredondamento e regras polimorficas coerentes com o valor persistido.");
        System.out.println("Este teste valida conversao e regra; nao executa registrarLeitura nem gera alertas em public.");
    }
}
