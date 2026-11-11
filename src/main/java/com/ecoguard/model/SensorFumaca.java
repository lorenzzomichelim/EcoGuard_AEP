
package com.ecoguard.model;

public class SensorFumaca extends DispositivoSensor {

    public SensorFumaca(int id, String nome, double valor) {
        super(id, nome, valor);
    }

    @Override
    public boolean processarLeitura() {
        return valor >= 150;
    }
}
