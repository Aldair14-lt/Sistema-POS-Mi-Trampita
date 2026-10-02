package MiTrampita.SistemaPOS.controller;

import MiTrampita.SistemaPOS.dto.*;
import MiTrampita.SistemaPOS.security.PosPrincipal;
import MiTrampita.SistemaPOS.service.VentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/pedidos-online") @RequiredArgsConstructor
public class OnlineOrderController {
    private final VentaService service;
    @GetMapping
    public List<VentaResponse> listar() { return service.listarOnline().stream().map(VentaResponse::from).toList(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse crear(@Valid @RequestBody PedidoOnlineRequest request, @AuthenticationPrincipal PosPrincipal principal) {
        return VentaResponse.from(service.registrarOnline(request, principal.id()));
    }
}
