package com.ecoguard.util;

public class Validacao {
    public static String textoObrigatorio(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new IllegalArgumentException("Preencha os campos obrigatorios.");
        }
        return texto.trim();
    }

    public static int idPositivo(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("O ID deve ser um inteiro positivo.");
        }
        return id;
    }

    public static String tipoSensor(String tipo) {
        tipo = textoObrigatorio(tipo).toUpperCase(java.util.Locale.ROOT);
        if (!tipo.equals("TEMPERATURA") && !tipo.equals("UMIDADE") && !tipo.equals("FUMACA")) {
            throw new IllegalArgumentException("Tipo invalido. Use TEMPERATURA, UMIDADE ou FUMACA.");
        }
        return tipo;
    }

    public static String statusSensor(String status) {
        status = textoObrigatorio(status).toUpperCase(java.util.Locale.ROOT);
        if (!status.equals("ATIVO") && !status.equals("INATIVO")) {
            throw new IllegalArgumentException("Status invalido. Use ATIVO ou INATIVO.");
        }
        return status;
    }

    public static double numeroFinito(double valor) {
        if (!Double.isFinite(valor)) {
            throw new IllegalArgumentException("A leitura deve ser um numero finito.");
        }
        return valor;
    }

    public static void leitura(String tipo, double valor) {
        numeroFinito(valor);
        tipo = tipoSensor(tipo);
        if (tipo.equals("UMIDADE") && (valor < 0 || valor > 100)) {
            throw new IllegalArgumentException("A umidade deve estar entre 0 e 100%.");
        }
        if (tipo.equals("FUMACA") && valor < 0) {
            throw new IllegalArgumentException("A fumaca nao pode ser negativa.");
        }
    }
}
