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
    private final Map<SseEmitter, Boolean> listeners = new ConcurrentHashMap<>();
    public SseEmitter subscribe(boolean kitchen) {
        var emitter = new SseEmitter(3_600_000L);
        listeners.put(emitter, kitchen);
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
        listeners.forEach((emitter, isKitchen) -> { if (kitchen || !isKitchen) send(emitter, "actualizar"); });
    }
    @Scheduled(fixedDelay = 25000)
    public void heartbeat() { listeners.keySet().forEach(e -> send(e, "latido")); }
    private void send(SseEmitter emitter, String event) {
        try { emitter.send(SseEmitter.event().name(event).data("1")); }
        catch (Exception error) { listeners.remove(emitter); emitter.complete(); }
    }
}
