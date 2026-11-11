
package com.ecoguard.model;

public class SensorTemperatura extends DispositivoSensor {

    public SensorTemperatura(int id, String nome, double valor) {
        super(id, nome, valor);
    }

    @Override
    public boolean processarLeitura() {
        return valor >= 55;
    }
}
