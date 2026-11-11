
package com.ecoguard.model;

public class SensorUmidade extends DispositivoSensor {

    public SensorUmidade(int id, String nome, double valor) {
        super(id, nome, valor);
    }

    @Override
    public boolean processarLeitura() {
        return valor <= 20;
    }
}
