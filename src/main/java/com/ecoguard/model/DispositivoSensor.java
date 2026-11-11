
package com.ecoguard.model;

import java.util.ArrayList;

public abstract class DispositivoSensor {

    protected int id;
    protected String nome;
    protected double valor;
    private final ArrayList<Leitura> leituras = new ArrayList<>();

    public DispositivoSensor(int id, String nome, double valor) {
        this.id = id;
        this.nome = nome;
        this.valor = valor;
    }

    public abstract boolean processarLeitura();

    // Composicao 1:N: o sensor mantem as leituras adicionadas a este objeto.
    // O historico completo continua no PostgreSQL, consultado pelo RelatorioDAO.
    public void adicionarLeitura(Leitura leitura) {
        if (leitura == null) {
            throw new IllegalArgumentException("Informe a leitura.");
        }
        leituras.add(leitura);
        valor = leitura.getValor();
    }

    public ArrayList<Leitura> getLeituras() {
        return new ArrayList<>(leituras);
    }
}
