package MiTrampita.SistemaPOS.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EstadoPreparacion {
    PENDIENTE, PREPARANDO, LISTO, SERVIDO;
    @JsonCreator
    public static EstadoPreparacion from(String value) {
        return "EN_PREPARACION".equals(value) ? PREPARANDO : valueOf(value);
    }
}
