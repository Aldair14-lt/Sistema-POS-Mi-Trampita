package MiTrampita.SistemaPOS.security;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

/** Identidad y permisos obtenidos del servidor, nunca del JSON del navegador. */
public record PosPrincipal(Integer id, String usuario, String nombreCompleto, List<String> roles)
        implements Serializable {
    public static String canonicalRole(String nombre) {
        return switch (nombre.trim().toUpperCase(Locale.ROOT)) {
            case "ADMIN", "ADMINISTRADOR" -> "ADMIN";
            case "MOZO", "VENTAS", "VENDEDOR", "MOZO/VENTAS" -> "MOZO";
            case "CAJA", "CAJERO" -> "CAJA";
            case "COCINERO" -> "COCINERO";
            case "BARTENDER" -> "BARTENDER";
            default -> "SIN_ACCESO";
        };
    }
}
