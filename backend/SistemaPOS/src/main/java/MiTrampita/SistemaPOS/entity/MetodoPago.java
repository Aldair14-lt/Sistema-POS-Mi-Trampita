package MiTrampita.SistemaPOS.entity;

public enum MetodoPago {
    EFECTIVO, TARJETA, TRANSFERENCIA, YAPE_PLIN, YAPE, PLIN;

    // Aceptar clientes anteriores que todavía envían los métodos en minúsculas.
    @com.fasterxml.jackson.annotation.JsonCreator
    public static MetodoPago from(String value) { return valueOf(value.toUpperCase(java.util.Locale.ROOT)); }

    @com.fasterxml.jackson.annotation.JsonValue
    public String codigo() { return name().toUpperCase(java.util.Locale.ROOT); }
}
