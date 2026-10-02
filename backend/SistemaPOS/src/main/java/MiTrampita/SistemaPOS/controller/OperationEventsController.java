package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.service.OperationEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController @RequiredArgsConstructor
public class OperationEventsController {
    private final OperationEvents events;
    @GetMapping(value = "/api/cocina/eventos", produces = "text/event-stream")
    public SseEmitter kitchen() { return events.subscribe(true); }
    @GetMapping(value = "/api/operacion/eventos", produces = "text/event-stream")
    public SseEmitter operation() { return events.subscribe(false); }
}
