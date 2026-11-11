package com.ecoguard.model;

public class Leitura {
    private final int id;
    private final double valor;
    private final String unidadeMedida;

    public Leitura(int id, double valor, String unidadeMedida) {
        this.id = id;
        this.valor = valor;
        this.unidadeMedida = unidadeMedida;
    }

    public int getId() { return id; }
    public double getValor() { return valor; }
    public String getUnidadeMedida() { return unidadeMedida; }
}
