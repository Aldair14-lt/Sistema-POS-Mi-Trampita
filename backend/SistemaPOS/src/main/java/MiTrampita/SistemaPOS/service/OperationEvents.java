package MiTrampita.SistemaPOS.service;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Solo publica invalidaciones, sin datos de clientes ni importes para Cocina. */
@Component
public class OperationEvents {
    private record Subscription(boolean kitchen, Integer userId) { }
    private final Map<SseEmitter, Subscription> listeners = new ConcurrentHashMap<>();
    public synchronized SseEmitter subscribe(boolean kitchen, Integer userId) {
        if (listeners.size() >= 500 || listeners.values().stream().filter(s -> s.userId().equals(userId)).count() >= 6)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "Cierra otras pestañas antes de abrir más tableros");
        // Cada reconexión vuelve a verificar la sesión, contraseña y roles en SecurityConfig.
        var emitter = new SseEmitter(300_000L);
        listeners.put(emitter, new Subscription(kitchen, userId));
        emitter.onCompletion(() -> listeners.remove(emitter));
        emitter.onTimeout(() -> { listeners.remove(emitter); emitter.complete(); });
        emitter.onError(error -> listeners.remove(emitter));
        send(emitter, "conectado");
        return emitter;
    }
    public void changed(boolean kitchen) {
        // Nunca anunciar una comanda o cobro que finalmente hizo rollback.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish(kitchen); }
            });
        } else publish(kitchen);
    }
    private void publish(boolean kitchen) {
        listeners.forEach((emitter, subscription) -> { if (kitchen || !subscription.kitchen()) send(emitter, "actualizar"); });
    }
    @Scheduled(fixedDelay = 25000)
    public void heartbeat() { listeners.keySet().forEach(e -> send(e, "latido")); }
    private void send(SseEmitter emitter, String event) {
        try { emitter.send(SseEmitter.event().name(event).data("1")); }
        catch (Exception error) {
            listeners.remove(emitter);
            try { emitter.complete(); } catch (RuntimeException ignored) { /* Una conexión cerrada no invalida el commit. */ }
        }
    }
}
