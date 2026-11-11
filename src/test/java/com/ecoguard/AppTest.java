package com.ecoguard;

import com.ecoguard.dao.*;
import com.ecoguard.model.*;
import com.ecoguard.util.Validacao;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.sql.SQLException;

// Testes sem bibliotecas adicionais e sem conexao com PostgreSQL.
public class AppTest {
    private static int verificacoes;
    private static int chamadas;
    private static int responsavel;

    public static void main(String[] args) {
        sensores();
        validacoes();
        permissoes();
        entradasInvalidas();
        System.out.println("EcoGuard: " + verificacoes + " verificacoes passaram (unitarias e simuladas; sem PostgreSQL).");
    }

    private static void verificar(boolean condicao, String descricao) {
        verificacoes++;
        if (!condicao) throw new AssertionError(descricao);
    }

    private static void sensores() {
        DispositivoSensor[] sensores = {
            new SensorTemperatura(1, "T", 54.9), new SensorTemperatura(1, "T", 55),
            new SensorUmidade(2, "U", 20.1), new SensorUmidade(2, "U", 20),
            new SensorFumaca(3, "F", 149.9), new SensorFumaca(3, "F", 150)
        };
        for (int i = 0; i < sensores.length; i++) {
            verificar(sensores[i].processarLeitura() == (i % 2 == 1), "Limite e polimorfismo " + i);
        }
        DispositivoSensor sensor = new SensorTemperatura(1, "T", 20);
        sensor.adicionarLeitura(new Leitura(10, 30, "C"));
        sensor.adicionarLeitura(new Leitura(11, 55, "C"));
        verificar(sensor.getLeituras().size() == 2, "Composicao 1:N");
        verificar(sensor.processarLeitura(), "Processamento usa ultima leitura");
        verificar(sensor.getLeituras().get(1).getId() == 11, "ID da leitura");
        verificar(sensor.getLeituras().get(1).getUnidadeMedida().equals("C"), "Unidade da leitura");
        sensor.getLeituras().clear();
        verificar(sensor.getLeituras().size() == 2, "Colecao encapsulada");
    }

    private static void rejeitar(String caso) {
        boolean rejeitou = false;
        try {
            switch (caso) {
                case "nome": Validacao.textoObrigatorio("   "); break;
                case "tipo": Validacao.tipoSensor("vento"); break;
                case "status": Validacao.statusSensor("quebrado"); break;
                case "id": Validacao.idPositivo(0); break;
                case "nan": Validacao.numeroFinito(Double.NaN); break;
                case "infinito": Validacao.numeroFinito(Double.POSITIVE_INFINITY); break;
                case "umidade negativa": Validacao.leitura("UMIDADE", -1); break;
                case "umidade alta": Validacao.leitura("UMIDADE", 101); break;
                case "fumaca negativa": Validacao.leitura("FUMACA", -1); break;
                default: throw new AssertionError("Caso desconhecido");
            }
        } catch (IllegalArgumentException e) {
            rejeitou = true;
        }
        verificar(rejeitou, "Rejeitar " + caso);
    }

    private static void validacoes() {
        for (String caso : new String[]{"nome", "tipo", "status", "id", "nan", "infinito",
                "umidade negativa", "umidade alta", "fumaca negativa"}) rejeitar(caso);
        verificar(Validacao.tipoSensor(" umidade ").equals("UMIDADE"), "Normalizar tipo");
        verificar(Validacao.statusSensor(" ativo ").equals("ATIVO"), "Normalizar status");
        Validacao.leitura("UMIDADE", 0);
        Validacao.leitura("UMIDADE", 100);
        Validacao.leitura("TEMPERATURA", -20);
        Validacao.leitura("FUMACA", 0);
        verificar(true, "Leituras validas nas fronteiras");
    }

    private static String executar(String perfil, String comandos) {
        chamadas = 0;
        responsavel = 0;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try (Scanner scanner = new Scanner(perfil + "\nsimulada\n" + comandos);
             PrintStream saida = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(saida);
            App.executar(scanner, new LoginSimulado(), new SensoresSimulados(), new LeiturasSimuladas(),
                    new RelatoriosSimulados(), new AlertasSimulados(), new EstacoesSimuladas());
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static void permissoes() {
        for (String perfil : new String[]{"ADMINISTRADOR", "ANALISTA", "BRIGADISTA"}) {
            for (int opcao = 1; opcao <= 10; opcao++) {
                boolean permitido = opcao == 2 || opcao == 6 || opcao == 7 || opcao == 9 || opcao == 10
                        || perfil.equals("ADMINISTRADOR")
                        || (perfil.equals("ANALISTA") && (opcao == 5 || opcao == 8));
                String dados = "";
                if (permitido) {
                    switch (opcao) {
                        case 1: dados = "1\nTeste\ntemperatura\nativo\n"; break;
                        case 3: dados = "1\nTeste\nativo\n"; break;
                        case 4: dados = "1\nEXCLUIR 1\n"; break;
                        case 5: dados = "1\n55,5\n"; break;
                        case 8: dados = "77\n"; break;
                    }
                }
                String saida = executar(perfil, opcao + "\n" + dados + "0\n");
                verificar(chamadas == (permitido ? 1 : 0), "DAO " + perfil + " opcao " + opcao);
                verificar(saida.contains("Acesso negado.") != permitido, "Mensagem " + perfil + " opcao " + opcao);
                verificar(saida.contains("Encerrando EcoGuard..."), "Retorno ao menu");
                if (permitido && opcao == 8) verificar(responsavel == 42, "Responsavel autenticado");
            }
        }
        verificar(!executar("INVALIDO", "").contains("MONITORAMENTO AMBIENTAL"), "Login recusado sem menu");
        verificar(!executar("DESCONHECIDO", "").contains("MONITORAMENTO AMBIENTAL"), "Perfil desconhecido recusado");
        verificar(executar("ADMINISTRADOR", "").contains("Entrada encerrada"), "Fim da entrada tratado");
        String falhaBanco = executar("FALHA_BANCO", "");
        verificar(falhaBanco.contains("Nao foi possivel acessar o banco"), "Falha JDBC identificada");
        verificar(!falhaBanco.contains("Email ou senha incorretos"), "Falha JDBC nao e login invalido");
        verificar(!falhaBanco.contains("MONITORAMENTO AMBIENTAL"), "Falha JDBC sem menu");
    }

    private static void entradaInvalida(String comandos, String mensagem) {
        String saida = executar("ADMINISTRADOR", comandos + "0\n");
        verificar(chamadas == 0, "Entrada invalida nao chega ao DAO: " + comandos);
        verificar(saida.contains(mensagem), "Mensagem de entrada invalida: " + mensagem);
        verificar(saida.contains("Encerrando EcoGuard..."), "Menu continua apos erro");
    }

    private static void entradasInvalidas() {
        entradaInvalida("abc\n", "numero valido");
        entradaInvalida("99\n", "Opcao invalida");
        entradaInvalida("1\nabc\n", "numero valido");
        entradaInvalida("1\n0\n", "inteiro positivo");
        entradaInvalida("1\n1\n   \n", "campos obrigatorios");
        entradaInvalida("1\n1\nTeste\nvento\n", "Tipo invalido");
        entradaInvalida("1\n1\nTeste\nTEMPERATURA\nruim\n", "Status invalido");
        entradaInvalida("3\n1\n   \n", "campos obrigatorios");
        entradaInvalida("5\n1\nabc\n", "numero valido");
        entradaInvalida("5\n1\nNaN\n", "numero finito");
        entradaInvalida("5\n1\nInfinity\n", "numero finito");
        entradaInvalida("8\n-1\n", "inteiro positivo");
        entradaInvalida("4\n1\nS\n", "Exclusao cancelada");
    }

    private static class LoginSimulado extends UsuarioDAO {
        @Override public Usuario autenticar(String email, String senha) throws SQLException {
            if (email.equals("FALHA_BANCO")) throw new SQLException("Indisponivel no teste.");
            if (email.equals("INVALIDO")) return null;
            return new Usuario(42, "Pessoa ficticia", email, email);
        }
    }
    private static class SensoresSimulados extends SensorDAO {
        @Override public void cadastrarSensor(int id, String nome, String tipo, String status) { chamadas++; }
        @Override public void listarSensores() { chamadas++; }
        @Override public void atualizarSensor(int id, String nome, String status) { chamadas++; }
        @Override public void excluirSensor(int id) { chamadas++; }
    }
    private static class LeiturasSimuladas extends LeituraDAO {
        @Override public void registrarLeitura(int id, double valor) {
            chamadas++;
            verificar(valor == 55.5, "Virgula decimal");
        }
    }
    private static class RelatoriosSimulados extends RelatorioDAO {
        @Override public void listarLeituras() { chamadas++; }
        @Override public void listarAlertas() { chamadas++; }
        @Override public void listarAlertasAbertos() { chamadas++; }
    }
    private static class AlertasSimulados extends AlertaDAO {
        @Override public void encerrarAlerta(int id, Usuario usuario) {
            chamadas++;
            responsavel = usuario.getId();
        }
    }
    private static class EstacoesSimuladas extends EstacaoDAO {
        @Override public void listarEstacoes() { chamadas++; }
    }
}
