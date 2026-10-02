package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.EstadoPreparacion;
import jakarta.validation.constraints.NotNull;

/** Estado esperado evita que un tablero desactualizado sobrescriba otro cambio. */
public record UpdateItemStatusRequest(@NotNull EstadoPreparacion estado,
                                     @NotNull EstadoPreparacion estadoActual) { }
