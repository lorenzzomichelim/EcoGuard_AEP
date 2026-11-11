package com.ecoguard;

import com.ecoguard.dao.*;
import com.ecoguard.model.Usuario;
import com.ecoguard.util.ConexaoBanco;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Testes reais com DAOs e menu em banco criado exclusivamente para esta execucao. */
public class FluxoIntegracaoTest {
    private static final String PRINCIPAL = "jdbc:postgresql://localhost:5432/ecoguard";
    private static final String MANUTENCAO = "jdbc:postgresql://localhost:5432/postgres";
    private static final String[] TABELAS = {"tb_estacao", "tb_sensor", "tb_leitura", "tb_alerta", "tb_usuario"};
    private static String bancoTeste;
    private static String senhaBanco;
    private static String senhaDemo;
    private static int verificacoes;
    private static final ByteArrayOutputStream saida = new ByteArrayOutputStream();
    private static Usuario administrador;
    private static Usuario analista;
    private static Usuario brigadista;

    public static void main(String[] args) throws Exception {
        senhaBanco = System.getenv("ECOGUARD_DB_PASSWORD");
        if (senhaBanco == null || senhaBanco.isEmpty()) throw new IllegalStateException("Configure ECOGUARD_DB_PASSWORD.");
        bancoTeste = "ecoguard_teste_" + UUID.randomUUID().toString().replace("-", "");
        // O nome nao vem de entrada externa e deve passar por esta barreira antes de CREATE/DROP.
        if (!bancoTeste.matches("ecoguard_teste_[a-f0-9]{32}") || bancoTeste.equals("ecoguard")) {
            throw new IllegalStateException("Nome de banco de teste invalido.");
        }
        String antes = assinaturaPrincipal();
        String propriedadeAnterior = System.getProperty("ecoguard.db.url");
        boolean criado = false;
        PrintStream original = System.out;
        try (Connection manutencao = DriverManager.getConnection(MANUTENCAO, "postgres", senhaBanco)) {
            try {
                try (Statement comando = manutencao.createStatement()) { comando.executeUpdate("CREATE DATABASE " + bancoTeste); }
                criado = true;
                System.setProperty("ecoguard.db.url", "jdbc:postgresql://localhost:5432/" + bancoTeste);
                senhaDemo = UUID.randomUUID().toString();
                executarScript("database/script_banco.sql", null);
                executarScript("database/dados_iniciais.sql", hash(senhaDemo));
                try (PrintStream captura = new PrintStream(saida, true, StandardCharsets.UTF_8)) {
                    System.setOut(captura);
                    conferirInstalacao();
                    instalarContadorDeEscritas();
                    crud();
                    leiturasEAlertas();
                    entradasEIds();
                    rollback();
                    fluxoMenu();
                    permissoes();
                } finally { System.setOut(original); }
            } finally {
                System.setOut(original);
                if (propriedadeAnterior == null) System.clearProperty("ecoguard.db.url");
                else System.setProperty("ecoguard.db.url", propriedadeAnterior);
                try {
                    verificar(antes.equals(assinaturaPrincipal()), "Banco principal: dados ou sequencias mudaram.");
                    original.println("Banco principal preservado: assinaturas das cinco tabelas e estados das cinco sequencias identicos.");
                } finally {
                    if (criado) {
                        try (Statement comando = manutencao.createStatement()) { comando.executeUpdate("DROP DATABASE " + bancoTeste); }
                        original.println("Banco isolado criado nesta execucao foi removido: " + bancoTeste);
                    }
                }
            }
        }
        original.println("FluxoIntegracaoTest: " + verificacoes + " verificacoes reais aprovadas; scripts, DAOs, menu, permissoes e rollback.");
    }

    private static String assinaturaPrincipal() throws SQLException {
        StringBuilder assinatura = new StringBuilder();
        try (Connection conexao = DriverManager.getConnection(PRINCIPAL, "postgres", senhaBanco)) {
            conexao.setReadOnly(true);
            conexao.setAutoCommit(false);
            try {
                for (String tabela : TABELAS) {
                    // Somente assinaturas em memoria: nenhum dado pessoal ou hash e impresso.
                    String sql = "SELECT count(*), md5(coalesce(string_agg(linha::text, '' ORDER BY linha::text), '')) FROM public." + tabela + " linha";
                    try (Statement comando = conexao.createStatement(); ResultSet r = comando.executeQuery(sql)) {
                        r.next(); assinatura.append(tabela).append(r.getLong(1)).append(r.getString(2));
                    }
                    String id = tabela.substring(3);
                    try (Statement comando = conexao.createStatement(); ResultSet r = comando.executeQuery(
                            "SELECT last_value, is_called FROM public." + tabela + "_id_" + id + "_seq")) {
                        r.next(); assinatura.append(r.getLong(1)).append(r.getBoolean(2));
                    }
                }
            } finally { conexao.rollback(); }
        }
        return assinatura.toString();
    }

    private static void executarScript(String arquivo, String senhaHash) throws Exception {
        ArrayList<String> argumentos = new ArrayList<>(Arrays.asList(
                "C:\\Program Files\\PostgreSQL\\18\\bin\\psql.exe", "-X", "-w", "-h", "localhost", "-p", "5432", "-U", "postgres",
                "-d", bancoTeste, "-v", "ON_ERROR_STOP=1", "-f", arquivo));
        if (senhaHash != null) { argumentos.add("-v"); argumentos.add("senha_hash=" + senhaHash); }
        ProcessBuilder processo = new ProcessBuilder(argumentos);
        processo.environment().put("PGPASSWORD", senhaBanco);
        processo.redirectErrorStream(true);
        Process execucao = processo.start();
        String resultado = new String(execucao.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (execucao.waitFor() != 0) throw new AssertionError("Script falhou: " + arquivo + " (saida omitida).");
        verificar(resultado.contains("COMMIT"), "Script nao concluiu a transacao: " + arquivo);
        System.out.println("Script executado no banco isolado: " + arquivo);
    }

    private static String hash(String senha) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(senha.getBytes(StandardCharsets.UTF_8));
        StringBuilder resultado = new StringBuilder();
        for (byte b : bytes) resultado.append(String.format("%02x", b & 0xff));
        return resultado.toString();
    }

    private static Connection abrirTeste() throws SQLException {
        Connection conexao = ConexaoBanco.conectar();
        try (Statement comando = conexao.createStatement(); ResultSet r = comando.executeQuery("SELECT current_database()")) {
            if (!r.next() || !bancoTeste.equals(r.getString(1))) {
                conexao.close(); throw new SQLException("Barreira de isolamento: conexao nao aponta para o banco criado pelo teste.");
            }
        }
        return conexao;
    }

    private static long numero(String sql, Object... parametros) throws SQLException {
        try (Connection conexao = abrirTeste(); PreparedStatement comando = conexao.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) comando.setObject(i + 1, parametros[i]);
            try (ResultSet r = comando.executeQuery()) {
                if (!r.next()) throw new AssertionError("Consulta de verificacao sem resultado.");
                return r.getLong(1);
            }
        }
    }

    private static void sql(String sql) throws SQLException {
        try (Connection conexao = abrirTeste(); Statement comando = conexao.createStatement()) { comando.execute(sql); }
    }
    private static void verificar(boolean condicao, String descricao) {
        verificacoes++;
        if (!condicao) throw new AssertionError(descricao);
    }
    private static void limparSaida() { saida.reset(); }
    private static void mensagem(String texto) {
        verificar(saida.toString(StandardCharsets.UTF_8).contains(texto), "Mensagem esperada ausente: " + texto);
    }
    private static int estacao() throws SQLException { return (int) numero("SELECT id_estacao FROM tb_estacao ORDER BY id_estacao LIMIT 1"); }
    private static int sensor(String tipo) throws SQLException {
        return (int) numero("SELECT id_sensor FROM tb_sensor WHERE tipo_sensor = ? ORDER BY id_sensor LIMIT 1", tipo);
    }
    private static long tentativas() throws SQLException {
        return numero("SELECT CASE WHEN is_called THEN last_value ELSE 0 END FROM public.integracao_tentativas_escrita");
    }

    private static void conferirInstalacao() throws SQLException {
        verificar(estrutura(PRINCIPAL).equals(estrutura(System.getProperty("ecoguard.db.url"))),
                "Scripts nao reproduzem os campos, constraints ou indices do banco principal.");
        verificar(numero("SELECT count(*) FROM tb_estacao") == 1, "Seed estacao");
        verificar(numero("SELECT count(*) FROM tb_sensor") == 3, "Seed sensores");
        verificar(numero("SELECT count(*) FROM tb_usuario") == 3, "Seed usuarios");
        verificar(numero("SELECT count(*) FROM tb_leitura") == 0, "Banco isolado sem historico anterior");
        UsuarioDAO dao = new UsuarioDAO();
        administrador = dao.autenticar("admin@ecoguard.exemplo", senhaDemo);
        analista = dao.autenticar("analista@ecoguard.exemplo", senhaDemo);
        brigadista = dao.autenticar("brigadista@ecoguard.exemplo", senhaDemo);
        verificar(administrador != null && administrador.getPerfil().equals("ADMINISTRADOR"), "Login administrador do seed");
        verificar(analista != null && analista.getPerfil().equals("ANALISTA"), "Login analista do seed");
        verificar(brigadista != null && brigadista.getPerfil().equals("BRIGADISTA"), "Login brigadista do seed");
        verificar(dao.autenticar("admin@ecoguard.exemplo", senhaDemo + "errada") == null, "Senha incorreta");
    }

    private static String estrutura(String url) throws SQLException {
        String filtro = "('tb_estacao', 'tb_sensor', 'tb_leitura', 'tb_alerta', 'tb_usuario')";
        String[] consultas = {
            "SELECT table_name, column_name, udt_name, character_maximum_length, numeric_precision, numeric_scale, "
                + "is_nullable, column_default, is_identity FROM information_schema.columns WHERE table_schema = 'public' "
                + "AND table_name IN " + filtro + " ORDER BY table_name, ordinal_position",
            "SELECT c.relname, k.conname, k.contype, pg_get_constraintdef(k.oid), k.convalidated "
                + "FROM pg_constraint k JOIN pg_class c ON c.oid = k.conrelid JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = 'public' AND c.relname IN " + filtro + " ORDER BY c.relname, k.conname",
            "SELECT tablename, indexname, indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename IN "
                + filtro + " ORDER BY tablename, indexname"
        };
        StringBuilder resultado = new StringBuilder();
        try (Connection conexao = DriverManager.getConnection(url, "postgres", senhaBanco)) {
            conexao.setReadOnly(true);
            conexao.setAutoCommit(false);
            try {
                for (String consulta : consultas) {
                    try (Statement comando = conexao.createStatement(); ResultSet r = comando.executeQuery(consulta)) {
                        while (r.next()) {
                            for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) resultado.append(r.getObject(i)).append('|');
                            resultado.append('\n');
                        }
                    }
                }
            } finally { conexao.rollback(); }
        }
        return resultado.toString();
    }

    private static void instalarContadorDeEscritas() throws SQLException {
        // Instrumentacao so no banco descartavel. nextval nao e desfeito por rollback:
        // detecta ate tentativas de INSERT/UPDATE/DELETE que nao afetem linhas ou sejam revertidas.
        sql("CREATE SEQUENCE public.integracao_tentativas_escrita");
        sql("CREATE FUNCTION public.contar_escrita_integracao() RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN PERFORM nextval('public.integracao_tentativas_escrita'); RETURN NULL; END; $$");
        for (String tabela : TABELAS) sql("CREATE TRIGGER contar_escrita BEFORE INSERT OR UPDATE OR DELETE ON public."
                + tabela + " FOR EACH STATEMENT EXECUTE FUNCTION public.contar_escrita_integracao()");
        verificar(tentativas() == 0, "Instrumentacao inicia zerada");
    }

    private static void crud() throws SQLException {
        SensorDAO dao = new SensorDAO();
        long antes = numero("SELECT count(*) FROM tb_sensor");
        dao.cadastrarSensor(estacao(), "Sensor CRUD", "TEMPERATURA", "ATIVO");
        verificar(numero("SELECT count(*) FROM tb_sensor") == antes + 1, "Cadastro persistido por DAO");
        int id = (int) numero("SELECT id_sensor FROM tb_sensor WHERE nome = ?", "Sensor CRUD");
        verificar(id > 0, "ID gerado pelo banco");
        limparSaida(); dao.listarSensores(); mensagem("Sensor CRUD");
        dao.atualizarSensor(id, "Sensor atualizado", "INATIVO");
        verificar(numero("SELECT count(*) FROM tb_sensor WHERE id_sensor = ? AND nome = ? AND status = 'INATIVO'", id, "Sensor atualizado") == 1, "Atualizacao persistida");
        limparSaida(); new LeituraDAO().registrarLeitura(id, 60); mensagem("Sensor inativo");
        verificar(numero("SELECT count(*) FROM tb_leitura WHERE id_sensor = ?", id) == 0, "Sensor inativo sem leitura");
        dao.excluirSensor(id);
        verificar(numero("SELECT count(*) FROM tb_sensor WHERE id_sensor = ?", id) == 0, "Exclusao do sensor de teste");
        verificar(numero("SELECT count(*) FROM tb_sensor") == antes, "CRUD preserva outros sensores do seed");
    }

    private static void leiturasEAlertas() throws SQLException {
        String[] tipos = {"TEMPERATURA", "UMIDADE", "FUMACA"};
        double[] normais = {25, 40, 50};
        double[] criticas = {54.999, 20.004, 149.999};
        LeituraDAO dao = new LeituraDAO();
        for (int i = 0; i < tipos.length; i++) {
            int id = sensor(tipos[i]);
            long leituras = numero("SELECT count(*) FROM tb_leitura");
            long alertas = numero("SELECT count(*) FROM tb_alerta");
            limparSaida(); dao.registrarLeitura(id, normais[i]); mensagem("Leitura normal.");
            verificar(numero("SELECT count(*) FROM tb_leitura") == leituras + 1, "Leitura normal persistida");
            verificar(numero("SELECT count(*) FROM tb_alerta") == alertas, "Normal nao gera alerta");
            limparSaida(); dao.registrarLeitura(id, criticas[i]); mensagem("ALERTA CRITICO!");
            verificar(numero("SELECT count(*) FROM tb_leitura") == leituras + 2, "Leitura critica persistida");
            verificar(numero("SELECT count(*) FROM tb_alerta") == alertas + 1, "Alerta gerado automaticamente");
            verificar(numero("SELECT count(*) FROM tb_alerta a JOIN tb_leitura l ON l.id_leitura = a.id_leitura WHERE l.id_sensor = ? AND a.status = 'ABERTO' AND a.nivel_criticidade = 'CRITICO'", id) >= 1, "Alerta ligado a leitura");
        }
        RelatorioDAO relatorio = new RelatorioDAO();
        limparSaida(); relatorio.listarLeituras(); mensagem("HISTORICO DE LEITURAS");
        limparSaida(); relatorio.listarAlertas(); mensagem("CRITICO");
        int alerta = (int) numero("SELECT id_alerta FROM tb_alerta ORDER BY id_alerta LIMIT 1");
        AlertaDAO alertas = new AlertaDAO();
        limparSaida(); alertas.encerrarAlerta(alerta, brigadista); mensagem("Acesso negado");
        verificar(numero("SELECT count(*) FROM tb_alerta WHERE id_alerta = ? AND status = 'ABERTO'", alerta) == 1, "Brigadista nao encerra");
        alertas.encerrarAlerta(alerta, analista);
        verificar(numero("SELECT count(*) FROM tb_alerta WHERE id_alerta = ? AND status = 'ENCERRADO' AND id_usuario_resp = ?", alerta, analista.getId()) == 1, "Encerramento com responsavel real");
        limparSaida(); alertas.encerrarAlerta(alerta, administrador); mensagem("ja encerrado");
        verificar(numero("SELECT id_usuario_resp FROM tb_alerta WHERE id_alerta = ?", alerta) == analista.getId(), "Responsavel inicial preservado");
        limparSaida(); relatorio.listarAlertas(); mensagem(analista.getNome());
    }

    private static void entradasEIds() throws SQLException {
        int sensorAusente = (int) numero("SELECT max(id_sensor) + 1 FROM tb_sensor");
        int alertaAusente = (int) numero("SELECT max(id_alerta) + 1 FROM tb_alerta");
        int estacaoAusente = (int) numero("SELECT max(id_estacao) + 1 FROM tb_estacao");
        SensorDAO sensores = new SensorDAO();
        limparSaida(); sensores.atualizarSensor(sensorAusente, "Ausente", "ATIVO"); mensagem("Sensor nao encontrado");
        limparSaida(); sensores.excluirSensor(sensorAusente); mensagem("Sensor nao encontrado");
        limparSaida(); new LeituraDAO().registrarLeitura(sensorAusente, 25); mensagem("Sensor nao encontrado");
        limparSaida(); new AlertaDAO().encerrarAlerta(alertaAusente, administrador); mensagem("Alerta nao encontrado");
        long total = numero("SELECT count(*) FROM tb_sensor");
        limparSaida(); sensores.cadastrarSensor(estacaoAusente, "Estacao ausente", "FUMACA", "ATIVO"); mensagem("Estacao nao encontrada");
        verificar(numero("SELECT count(*) FROM tb_sensor") == total, "FK inexistente nao insere sensor");
        double[] valores = {-1, 101};
        for (double valor : valores) {
            long escritas = tentativas();
            boolean recusou = false;
            try { new LeituraDAO().registrarLeitura(sensor("UMIDADE"), valor); }
            catch (IllegalArgumentException e) { recusou = true; }
            verificar(recusou && tentativas() == escritas, "Umidade invalida bloqueada antes de DML");
        }
    }

    private static void rollback() throws SQLException {
        LeituraDAO leituras = new LeituraDAO();
        long antesLeituras = numero("SELECT count(*) FROM tb_leitura");
        long antesAlertas = numero("SELECT count(*) FROM tb_alerta");
        sql("ALTER TABLE tb_alerta ADD CONSTRAINT ck_integracao_rollback CHECK (nivel_criticidade <> 'CRITICO') NOT VALID");
        try {
            limparSaida(); leituras.registrarLeitura(sensor("TEMPERATURA"), 60); mensagem("Erro ao registrar leitura");
            verificar(numero("SELECT count(*) FROM tb_leitura") == antesLeituras, "Rollback remove leitura se alerta falha");
            verificar(numero("SELECT count(*) FROM tb_alerta") == antesAlertas, "Rollback mantem alertas anteriores");
            verificar(!saida.toString(StandardCharsets.UTF_8).contains("Leitura registrada com sucesso"), "Sem falso sucesso antes do commit");
        } finally { sql("ALTER TABLE tb_alerta DROP CONSTRAINT ck_integracao_rollback"); }
        int alerta = (int) numero("SELECT id_alerta FROM tb_alerta WHERE status = 'ABERTO' ORDER BY id_alerta LIMIT 1");
        int usuarioAusente = (int) numero("SELECT max(id_usuario) + 1 FROM tb_usuario");
        limparSaida(); new AlertaDAO().encerrarAlerta(alerta, new Usuario(usuarioAusente, "Inexistente", "inexistente@invalid", "ADMINISTRADOR"));
        mensagem("Erro ao encerrar alerta");
        verificar(numero("SELECT count(*) FROM tb_alerta WHERE id_alerta = ? AND status = 'ABERTO' AND id_usuario_resp IS NULL", alerta) == 1, "Rollback preserva alerta se responsavel viola FK");
        leituras.registrarLeitura(sensor("TEMPERATURA"), 20);
        verificar(numero("SELECT count(*) FROM tb_leitura") == antesLeituras + 1, "DAO continua funcional apos rollback");
    }

    private static void menu(Usuario usuario, String comandos) {
        limparSaida();
        try (Scanner scanner = new Scanner(usuario.getEmail() + "\n" + senhaDemo + "\n" + comandos)) {
            App.executar(scanner, new UsuarioDAO(), new SensorDAO(), new LeituraDAO(), new RelatorioDAO(), new AlertaDAO(), new EstacaoDAO());
        }
        mensagem("Login realizado com sucesso"); mensagem("Perfil: " + usuario.getPerfil()); mensagem("Encerrando EcoGuard");
    }

    private static void fluxoMenu() throws SQLException {
        menu(administrador, "1\n" + estacao() + "\nSensor menu\nTEMPERATURA\nATIVO\n0\n");
        int id = (int) numero("SELECT id_sensor FROM tb_sensor WHERE nome = ?", "Sensor menu");
        verificar(id > 0, "Menu cadastra via DAO real");
        menu(administrador, "2\n3\n" + id + "\nSensor menu atualizado\nATIVO\n5\n" + id + "\n25\n5\n" + id + "\n54,999\n6\n7\n9\n10\n0\n");
        verificar(numero("SELECT count(*) FROM tb_sensor WHERE id_sensor = ? AND nome = ?", id, "Sensor menu atualizado") == 1, "Menu atualiza");
        verificar(numero("SELECT count(*) FROM tb_leitura WHERE id_sensor = ?", id) == 2, "Menu grava leituras");
        int alerta = (int) numero("SELECT a.id_alerta FROM tb_alerta a JOIN tb_leitura l ON l.id_leitura = a.id_leitura WHERE l.id_sensor = ?", id);
        menu(administrador, "8\n" + alerta + "\n7\n0\n");
        verificar(numero("SELECT count(*) FROM tb_alerta WHERE id_alerta = ? AND status = 'ENCERRADO' AND id_usuario_resp = ?", alerta, administrador.getId()) == 1, "Menu encerra com usuario autenticado");
        menu(administrador, "4\n" + id + "\nS\n0\n");
        verificar(numero("SELECT count(*) FROM tb_sensor WHERE id_sensor = ?", id) == 1, "Confirmacao incorreta nao exclui");
        menu(administrador, "4\n" + id + "\nEXCLUIR " + id + "\n0\n");
        verificar(numero("SELECT count(*) FROM tb_sensor WHERE id_sensor = ?", id) == 0, "Menu exclui sensor isolado");
        verificar(numero("SELECT count(*) FROM tb_leitura WHERE id_sensor = ?", id) == 0, "Cascata de leituras no banco isolado");
        verificar(numero("SELECT count(*) FROM tb_alerta WHERE id_alerta = ?", alerta) == 0, "Cascata de alertas no banco isolado");
        int sensor = sensor("TEMPERATURA");
        menu(analista, "5\n" + sensor + "\n60\n0\n");
        int novoAlerta = (int) numero("SELECT max(id_alerta) FROM tb_alerta");
        menu(analista, "8\n" + novoAlerta + "\n0\n");
        verificar(numero("SELECT id_usuario_resp FROM tb_alerta WHERE id_alerta = ?", novoAlerta) == analista.getId(), "Analista registra e encerra pelo menu");
    }

    private static void permissoes() throws SQLException {
        Usuario[] usuarios = {analista, brigadista};
        for (Usuario usuario : usuarios) {
            int[] bloqueadas = usuario.getPerfil().equals("ANALISTA") ? new int[]{1, 3, 4} : new int[]{1, 3, 4, 5, 8};
            for (int opcao : bloqueadas) {
                long antes = tentativas();
                menu(usuario, opcao + "\n0\n"); mensagem("Acesso negado");
                verificar(tentativas() == antes, "Operacao proibida nao executa DML: " + usuario.getPerfil() + " opcao " + opcao);
            }
            long antes = tentativas();
            menu(usuario, "2\n6\n7\n9\n10\n0\n");
            verificar(tentativas() == antes, "Consultas de perfil nao executam DML");
        }
        String[] invalidas = {"abc\n0\n", "99\n0\n", "1\nabc\n0\n", "1\n" + estacao() + "\n   \n0\n",
            "1\n" + estacao() + "\nTeste\nVENTO\n0\n", "1\n" + estacao() + "\nTeste\nTEMPERATURA\nRUIM\n0\n",
            "5\n" + sensor("TEMPERATURA") + "\nNaN\n0\n", "5\n" + sensor("TEMPERATURA") + "\ntexto\n0\n"};
        for (String entrada : invalidas) {
            long antes = tentativas(); menu(administrador, entrada);
            verificar(tentativas() == antes, "Entrada invalida nao executa DML");
        }
        long antes = tentativas(); limparSaida();
        try (Scanner scanner = new Scanner("nao-cadastrado@invalid\n" + UUID.randomUUID() + "\n")) {
            App.executar(scanner, new UsuarioDAO(), new SensorDAO(), new LeituraDAO(), new RelatorioDAO(), new AlertaDAO(), new EstacaoDAO());
        }
        mensagem("Email ou senha incorretos");
        verificar(!saida.toString(StandardCharsets.UTF_8).contains("MONITORAMENTO AMBIENTAL"), "Sem autenticacao nao ha menu");
        verificar(tentativas() == antes, "Login recusado sem DML");
    }
}
