package MiTrampita.SistemaPOS.dto;

import java.util.List;

public record CajaResponse(List<VentaResponse> mesasPorCobrar, List<VentaResponse> otrasMesas,
        List<VentaResponse> pedidosOnline, List<VentaResponse> comprobantesRecientes) { }
