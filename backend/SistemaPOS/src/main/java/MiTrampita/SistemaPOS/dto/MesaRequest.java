package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.Mesa;
import jakarta.validation.constraints.*;

public record MesaRequest(@NotNull @Min(1) Integer numero, @NotNull @Min(1) @Max(100) Integer capacidad,
            @NotNull(message = "El área es obligatoria") @Min(1) Integer areaId) { }
