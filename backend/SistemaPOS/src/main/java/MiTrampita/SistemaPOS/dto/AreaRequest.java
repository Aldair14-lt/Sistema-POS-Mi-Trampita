package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.Area;
import jakarta.validation.constraints.*;

public record AreaRequest(@NotBlank @Size(max = 100) String nombre, @NotNull Area.EstadoArea estado) { }
