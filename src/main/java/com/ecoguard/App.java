package com.ecoguard;

import com.ecoguard.dao.*;
import com.ecoguard.model.Usuario;
import com.ecoguard.util.Validacao;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.sql.SQLException;

public class App {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            executar(scanner, new UsuarioDAO(), new SensorDAO(), new LeituraDAO(),
                    new RelatorioDAO(), new AlertaDAO(), new EstacaoDAO());
        }
    }

    // Os DAOs podem ser substituidos por simulacoes nos testes, sem acessar o banco.
    public static void executar(Scanner scanner, UsuarioDAO usuarioDAO, SensorDAO sensorDAO,
            LeituraDAO leituraDAO, RelatorioDAO relatorioDAO, AlertaDAO alertaDAO, EstacaoDAO estacaoDAO) {
        try {
            System.out.println("===== LOGIN ECOGUARD =====");
            String email = texto(scanner, "Email: ");
            String senha = entrada(scanner, "Senha: ");
            if (senha.isBlank()) {
                System.out.println("Informe a senha.");
                return;
            }
            Usuario usuario = usuarioDAO.autenticar(email, senha);
            if (usuario == null) {
                System.out.println("Email ou senha incorretos.");
                return;
            }
            String perfil = usuario.getPerfil();
            if (!"ADMINISTRADOR".equals(perfil) && !"ANALISTA".equals(perfil) && !"BRIGADISTA".equals(perfil)) {
                System.out.println("Perfil sem acesso ao sistema.");
                return;
            }
            System.out.println("Login realizado com sucesso!");
            int opcao = -1;
            do {
                System.out.println("\n===== ECOGUARD - MONITORAMENTO AMBIENTAL =====");
                System.out.println("Usuario: " + usuario.getNome() + " | Perfil: " + perfil);
                System.out.println("1 - Cadastrar sensor");
                System.out.println("2 - Listar sensores");
                System.out.println("3 - Atualizar sensor");
                System.out.println("4 - Excluir sensor");
                System.out.println("5 - Registrar leitura");
                System.out.println("6 - Historico de leituras");
                System.out.println("7 - Listar alertas");
                System.out.println("8 - Encerrar alerta");
                System.out.println("9 - Listar estacoes");
                System.out.println("10 - Listar alertas abertos");
                System.out.println("0 - Sair");
                try {
                    opcao = Integer.parseInt(entrada(scanner, "Escolha uma opcao: ").trim());
                    switch (opcao) {
                        case 1:
                            if (!perfil.equals("ADMINISTRADOR")) {
                                System.out.println("Acesso negado.");
                                break;
                            }
                            int idEstacao = id(scanner, "ID da estacao (consulte na opcao 9): ");
                            String nome = texto(scanner, "Nome do sensor: ");
                            String tipo = Validacao.tipoSensor(entrada(scanner, "Tipo (TEMPERATURA, UMIDADE, FUMACA): "));
                            String status = Validacao.statusSensor(entrada(scanner, "Status (ATIVO, INATIVO): "));
                            sensorDAO.cadastrarSensor(idEstacao, nome, tipo, status);
                            break;
                        case 2:
                            sensorDAO.listarSensores();
                            break;
                        case 3:
                            if (!perfil.equals("ADMINISTRADOR")) {
                                System.out.println("Acesso negado.");
                                break;
                            }
                            int idAtualizar = id(scanner, "ID do sensor: ");
                            String novoNome = texto(scanner, "Novo nome: ");
                            String novoStatus = Validacao.statusSensor(entrada(scanner, "Novo status (ATIVO, INATIVO): "));
                            sensorDAO.atualizarSensor(idAtualizar, novoNome, novoStatus);
                            break;
                        case 4:
                            if (!perfil.equals("ADMINISTRADOR")) {
                                System.out.println("Acesso negado.");
                                break;
                            }
                            int idExcluir = id(scanner, "ID do sensor a excluir: ");
                            System.out.println("Atencao: a exclusao pode apagar leituras e alertas associados.");
                            String confirmacao = entrada(scanner, "Digite EXCLUIR " + idExcluir + " para confirmar: ");
                            if (confirmacao.equals("EXCLUIR " + idExcluir)) {
                                sensorDAO.excluirSensor(idExcluir);
                            } else {
                                System.out.println("Exclusao cancelada.");
                            }
                            break;
                        case 5:
                            if (!perfil.equals("ADMINISTRADOR") && !perfil.equals("ANALISTA")) {
                                System.out.println("Acesso negado.");
                                break;
                            }
                            int idSensor = id(scanner, "ID do sensor: ");
                            double valor = Validacao.numeroFinito(Double.parseDouble(
                                    entrada(scanner, "Valor da leitura: ").trim().replace(",", ".")));
                            leituraDAO.registrarLeitura(idSensor, valor);
                            break;
                        case 6:
                            relatorioDAO.listarLeituras();
                            break;
                        case 7:
                            relatorioDAO.listarAlertas();
                            break;
                        case 8:
                            if (!perfil.equals("ADMINISTRADOR") && !perfil.equals("ANALISTA")) {
                                System.out.println("Acesso negado.");
                                break;
                            }
                            alertaDAO.encerrarAlerta(id(scanner, "ID do alerta: "), usuario);
                            break;
                        case 9:
                            estacaoDAO.listarEstacoes();
                            break;
                        case 10:
                            relatorioDAO.listarAlertasAbertos();
                            break;
                        case 0:
                            System.out.println("Encerrando EcoGuard...");
                            break;
                        default:
                            System.out.println("Opcao invalida.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Digite um numero valido. Operacao cancelada.");
                } catch (IllegalArgumentException e) {
                    System.out.println(e.getMessage() + " Operacao cancelada.");
                }
            } while (opcao != 0);
        } catch (SQLException e) {
            System.out.println("Nao foi possivel acessar o banco: " + e.getMessage());
        } catch (NoSuchElementException e) {
            System.out.println("\nEntrada encerrada. Encerrando EcoGuard...");
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }

    private static String entrada(Scanner scanner, String mensagem) {
        System.out.print(mensagem);
        return scanner.nextLine();
    }

    private static String texto(Scanner scanner, String mensagem) {
        return Validacao.textoObrigatorio(entrada(scanner, mensagem));
    }

    private static int id(Scanner scanner, String mensagem) {
        return Validacao.idPositivo(Integer.parseInt(entrada(scanner, mensagem).trim()));
    }
}
