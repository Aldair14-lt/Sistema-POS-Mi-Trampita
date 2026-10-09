package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.service.OperationEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController @RequiredArgsConstructor
public class OperationEventsController {
    private final OperationEvents events;
    @GetMapping(value = "/api/cocina/eventos", produces = "text/event-stream")
    public SseEmitter kitchen(@org.springframework.security.core.annotation.AuthenticationPrincipal MiTrampita.SistemaPOS.security.PosPrincipal user) {
        return events.subscribe(true, user.id());
    }
    @GetMapping(value = "/api/operacion/eventos", produces = "text/event-stream")
    public SseEmitter operation(@org.springframework.security.core.annotation.AuthenticationPrincipal MiTrampita.SistemaPOS.security.PosPrincipal user) {
        return events.subscribe(false, user.id());
    }
}
