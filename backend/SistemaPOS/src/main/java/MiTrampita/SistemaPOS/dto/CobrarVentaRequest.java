package MiTrampita.SistemaPOS.dto;

import jakarta.validation.Valid;

/** Un pago opcional permite emitir una cuenta que ya fue abonada íntegramente. */
public record CobrarVentaRequest(@Valid PagoParcialRequest pago) { }
