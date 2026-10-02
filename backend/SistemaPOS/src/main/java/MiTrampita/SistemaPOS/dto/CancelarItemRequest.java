package MiTrampita.SistemaPOS.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarItemRequest(@NotBlank @Size(max = 255) String motivo) { }
