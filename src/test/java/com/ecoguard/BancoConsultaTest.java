package com.ecoguard;

import com.ecoguard.util.ConexaoBanco;
import com.ecoguard.dao.UsuarioDAO;
import com.ecoguard.dao.SensorDAO;
import com.ecoguard.dao.EstacaoDAO;
import com.ecoguard.dao.RelatorioDAO;
import com.ecoguard.model.Usuario;
import java.sql.*;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

// Verificacao opcional de integracao. Apenas consultas, sem imprimir dados de usuarios.
public class BancoConsultaTest {
    public static void main(String[] args) throws SQLException {
        if (System.getenv("ECOGUARD_DB_PASSWORD") == null
                || System.getenv("ECOGUARD_DB_PASSWORD").isEmpty()) {
            throw new IllegalStateException("Configure ECOGUARD_DB_PASSWORD para o teste real de consulta.");
        }
        String[] consultas = {
            "SELECT id_estacao, nome, area_reserva, latitude, longitude, data_criacao FROM TB_ESTACAO WHERE FALSE",
            "SELECT id_sensor, id_estacao, nome, tipo_sensor, latitude, longitude, status, data_instalacao FROM TB_SENSOR WHERE FALSE",
            "SELECT id_leitura, id_sensor, data_hora, valor, unidade_medida FROM TB_LEITURA WHERE FALSE",
            "SELECT id_alerta, id_leitura, id_usuario_resp, data_hora, nivel_criticidade, status FROM TB_ALERTA WHERE FALSE",
            "SELECT id_usuario, nome, email, senha_hash, perfil, data_cadastro FROM TB_USUARIO WHERE FALSE",
            "SELECT a.id_alerta, s.tipo_sensor, l.valor, l.unidade_medida, u.nome AS responsavel FROM TB_ALERTA a "
                + "JOIN TB_LEITURA l ON a.id_leitura = l.id_leitura JOIN TB_SENSOR s ON l.id_sensor = s.id_sensor "
                + "LEFT JOIN TB_USUARIO u ON a.id_usuario_resp = u.id_usuario WHERE FALSE"
        };
        try (Connection conexao = ConexaoBanco.conectar()) {
            conexao.setReadOnly(true);
            conexao.setAutoCommit(false);
            try {
                try (Statement comando = conexao.createStatement()) {
                    comando.execute("SET LOCAL statement_timeout = '15s'");
                }
                for (String sql : consultas) {
                    try (PreparedStatement comando = conexao.prepareStatement(sql);
                         ResultSet resultado = comando.executeQuery()) {
                        if (resultado.next()) throw new AssertionError("Consulta deveria retornar zero registros.");
                    }
                }
                verificarRelacionamentos(conexao);
                conferirTipos(conexao);
                System.out.println("PostgreSQL real: conexao e " + consultas.length + " consultas de estrutura passaram.");
            } finally {
                conexao.rollback();
            }
        }
        verificarAutenticacao();
        verificarConsultasDosDaos();
        System.out.println("Nenhuma gravacao foi executada; testes de escrita continuam separados.");
    }

    private static void verificarConsultasDosDaos() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try (PrintStream captura = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(captura);
            new SensorDAO().listarSensores();
            new EstacaoDAO().listarEstacoes();
            RelatorioDAO relatorio = new RelatorioDAO();
            relatorio.listarLeituras();
            relatorio.listarAlertas();
            relatorio.listarAlertasAbertos();
        } finally {
            System.setOut(original);
        }
        if (buffer.toString(StandardCharsets.UTF_8).contains("Erro ao")) {
            throw new AssertionError("Um DAO de consulta apresentou erro. Dados da consulta omitidos.");
        }
        System.out.println("Cinco consultas pelos DAOs reais passaram: sensores, estacoes, historico e dois relatorios de alertas.");
    }

    private static void verificarRelacionamentos(Connection conexao) throws SQLException {
        String[] verificacoes = {
            "SELECT count(*) FROM TB_SENSOR s LEFT JOIN TB_ESTACAO e ON e.id_estacao = s.id_estacao WHERE e.id_estacao IS NULL",
            "SELECT count(*) FROM TB_LEITURA l LEFT JOIN TB_SENSOR s ON s.id_sensor = l.id_sensor WHERE s.id_sensor IS NULL",
            "SELECT count(*) FROM TB_ALERTA a LEFT JOIN TB_LEITURA l ON l.id_leitura = a.id_leitura WHERE l.id_leitura IS NULL",
            "SELECT count(*) FROM TB_ALERTA a LEFT JOIN TB_USUARIO u ON u.id_usuario = a.id_usuario_resp "
                + "WHERE a.id_usuario_resp IS NOT NULL AND u.id_usuario IS NULL"
        };
        for (String sql : verificacoes) {
            try (PreparedStatement comando = conexao.prepareStatement(sql);
                 ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next() || resultado.getLong(1) != 0) {
                    throw new AssertionError("Relacionamento com registros sem referencia. Execute a auditoria SQL.");
                }
            }
        }
        // Executa consultas com registros reais e os dois filtros do relatorio, sem expor linhas.
        String sql = "SELECT a.id_alerta, s.nome, s.tipo_sensor, l.valor, l.unidade_medida, "
                + "a.nivel_criticidade, a.status, a.data_hora, u.nome AS responsavel FROM TB_ALERTA a "
                + "JOIN TB_LEITURA l ON a.id_leitura = l.id_leitura "
                + "JOIN TB_SENSOR s ON l.id_sensor = s.id_sensor "
                + "LEFT JOIN TB_USUARIO u ON a.id_usuario_resp = u.id_usuario "
                + "WHERE (? = FALSE OR a.status = 'ABERTO') ORDER BY a.id_alerta DESC";
        for (boolean abertos : new boolean[]{false, true}) {
            try (PreparedStatement comando = conexao.prepareStatement(sql)) {
                comando.setBoolean(1, abertos);
                try (ResultSet resultado = comando.executeQuery()) {
                    int quantidade = 0;
                    while (resultado.next()) {
                        resultado.getInt("id_alerta");
                        resultado.getDouble("valor");
                        resultado.getTimestamp("data_hora");
                        resultado.getString("responsavel");
                        if (abertos && !"ABERTO".equals(resultado.getString("status"))) {
                            throw new AssertionError("Filtro de alertas abertos incorreto.");
                        }
                        quantidade++;
                    }
                    System.out.println((abertos ? "Alertas abertos" : "Todos os alertas") + ": " + quantidade);
                }
            }
        }
        System.out.println("Quatro verificacoes de integridade referencial passaram.");
    }

    private static void conferirTipos(Connection conexao) throws SQLException {
        String sql = "SELECT table_name, column_name, data_type, character_maximum_length, "
                + "numeric_precision, numeric_scale, is_nullable, column_default, is_identity "
                + "FROM information_schema.columns WHERE table_schema = 'public' "
                + "AND table_name IN ('tb_estacao', 'tb_sensor', 'tb_leitura', 'tb_alerta', 'tb_usuario') "
                + "ORDER BY table_name, ordinal_position";
        try (PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {
            while (resultado.next()) {
                System.out.println(resultado.getString("table_name") + "." + resultado.getString("column_name")
                        + " | " + resultado.getString("data_type")
                        + " | tamanho=" + resultado.getObject("character_maximum_length")
                        + " | precisao=" + resultado.getObject("numeric_precision")
                        + " | escala=" + resultado.getObject("numeric_scale")
                        + " | nulo=" + resultado.getString("is_nullable")
                        + " | padrao=" + resultado.getString("column_default")
                        + " | identidade=" + resultado.getString("is_identity"));
            }
        }
    }

    private static void verificarAutenticacao() throws SQLException {
        UsuarioDAO dao = new UsuarioDAO();
        String inexistente = "integracao-" + UUID.randomUUID() + "@invalid";
        String senhaFicticia = UUID.randomUUID().toString();
        if (dao.autenticar(inexistente, senhaFicticia) != null
                || dao.autenticar("' OR '1'='1", senhaFicticia) != null) {
            throw new AssertionError("Autenticacao permitiu credenciais invalidas.");
        }
        System.out.println("Login inexistente e entrada SQL tratada como parametro: recusados corretamente.");
        String email = System.getenv("ECOGUARD_TEST_EMAIL");
        String senha = System.getenv("ECOGUARD_TEST_PASSWORD");
        if (email == null || senha == null || email.isBlank() || senha.isEmpty()) {
            System.out.println("Login valido NAO TESTADO: configure ECOGUARD_TEST_EMAIL e ECOGUARD_TEST_PASSWORD se desejar.");
            return;
        }
        Usuario usuario = dao.autenticar(email, senha);
        if (usuario == null || usuario.getId() <= 0
                || (!"ADMINISTRADOR".equals(usuario.getPerfil()) && !"ANALISTA".equals(usuario.getPerfil())
                && !"BRIGADISTA".equals(usuario.getPerfil()))) {
            throw new AssertionError("Login de teste nao retornou um usuario com perfil valido.");
        }
        System.out.println("Login valido aprovado; perfil: " + usuario.getPerfil());
    }
}
