package MiTrampita.SistemaPOS.security;

import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Límite acotado por dirección de conexión; no confía en X-Forwarded-For del cliente. */
@Component
public class PublicOrderRateLimiter {
    private record Ventana(long inicio, int intentos) { }
    private final Map<String, Ventana> ventanas = new HashMap<>();
    public synchronized void validar(String direccion) {
        long ahora = System.nanoTime(); long minuto = 60_000_000_000L;
        ventanas.entrySet().removeIf(e -> ahora - e.getValue().inicio() >= minuto);
        var ventana = ventanas.get(direccion);
        if ((ventana == null && ventanas.size() >= 10000) || (ventana != null && ventana.intentos() >= 10))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Espera un minuto y vuelve a intentar");
        ventanas.put(direccion, new Ventana(ventana == null ? ahora : ventana.inicio(), ventana == null ? 1 : ventana.intentos() + 1));
    }
}
