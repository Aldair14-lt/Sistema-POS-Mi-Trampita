package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.Cliente;
import java.time.LocalDate;

/** Información necesaria para atender al cliente, sin sus métricas administrativas. */
public record ClientePosResponse(Integer id, String numeroDocumento, String nombresRazonSocial,
        String direccion, String telefono, String correo, LocalDate fechaNacimiento) {
    public static ClientePosResponse from(Cliente c) {
        if (c == null) return null;
        return new ClientePosResponse(c.getId(), c.getNumeroDocumento(), c.getNombresRazonSocial(),
                c.getDireccion(), c.getTelefono(), c.getCorreo(), c.getFechaNacimiento());
    }
}

