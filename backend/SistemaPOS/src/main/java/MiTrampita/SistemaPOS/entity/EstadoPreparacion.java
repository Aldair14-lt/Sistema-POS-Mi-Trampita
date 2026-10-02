package MiTrampita.SistemaPOS.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EstadoPreparacion {
    PENDIENTE, PREPARANDO, LISTO, SERVIDO, CANCELADO;
    @JsonCreator
    public static EstadoPreparacion from(String value) {
        return "EN_PREPARACION".equals(value) ? PREPARANDO : valueOf(value);
    }
}
