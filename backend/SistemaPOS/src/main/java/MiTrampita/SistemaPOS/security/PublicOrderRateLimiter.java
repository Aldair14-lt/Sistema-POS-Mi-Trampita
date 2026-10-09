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
    private final Map<String, Ventana> lecturas = new HashMap<>();
    public synchronized void validar(String direccion) {
        validar(direccion, ventanas, 10);
    }
    public synchronized void validarLectura(String direccion) {
        validar(direccion, lecturas, 120);
    }
    private void validar(String direccion, Map<String, Ventana> registro, int maximo) {
        long ahora = System.nanoTime(); long minuto = 60_000_000_000L;
        registro.entrySet().removeIf(e -> ahora - e.getValue().inicio() >= minuto);
        var ventana = registro.get(direccion);
        if ((ventana == null && registro.size() >= 10000) || (ventana != null && ventana.intentos() >= maximo))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Espera un minuto y vuelve a intentar");
        registro.put(direccion, new Ventana(ventana == null ? ahora : ventana.inicio(), ventana == null ? 1 : ventana.intentos() + 1));
    }
}
