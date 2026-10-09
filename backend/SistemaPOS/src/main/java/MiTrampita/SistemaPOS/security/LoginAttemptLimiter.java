package MiTrampita.SistemaPOS.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Límite temporal de fallos, por conexión y usuario; no bloquea permanentemente cuentas. */
@Component
public class LoginAttemptLimiter {
    private record Window(long start, int failures) { }
    private final Map<String, Window> windows = new HashMap<>();
    private static final long MINUTE = 60_000_000_000L;
    public synchronized void check(String address, String username) {
        long now = System.nanoTime();
        windows.entrySet().removeIf(e -> now - e.getValue().start() >= MINUTE);
        if (windows.size() >= 10000 || failures("ip:" + address) >= 30 || failures(userKey(username)) >= 5)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos de acceso. Espera un minuto");
    }
    public synchronized void failed(String address, String username) {
        record("ip:" + address); record(userKey(username));
    }
    public synchronized void succeeded(String username) { windows.remove(userKey(username)); }
    private int failures(String key) { var value = windows.get(key); return value == null ? 0 : value.failures(); }
    private String userKey(String username) { return "user:" + username.trim().toLowerCase(Locale.ROOT); }
    private void record(String key) {
        if (windows.size() >= 10000 && !windows.containsKey(key)) return;
        var prior = windows.get(key); long now = System.nanoTime();
        if (prior == null || now - prior.start() >= MINUTE) windows.put(key, new Window(now, 1));
        else windows.put(key, new Window(prior.start(), prior.failures() + 1));
    }
}
