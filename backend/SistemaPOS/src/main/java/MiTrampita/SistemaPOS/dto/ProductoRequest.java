package MiTrampita.SistemaPOS.dto;

import MiTrampita.SistemaPOS.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Acepta referencias por ID, nunca entidades anidadas ni metadatos de persistencia. */
public record ProductoRequest(@PositiveOrZero Long version,
        @NotNull @Valid Referencia categoria, @Valid Referencia marca,
        @NotNull @Valid Referencia proveedor,
        @NotBlank @Size(max = 50) String codigoBarras,
        @NotBlank @Size(max = 150) String nombre, @Size(max = 5000) String descripcion,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal precioCompra,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal precioVenta,
        @NotNull @Min(0) Integer stockActual, @NotNull @Min(0) Integer stockMinimo,
        AreaDestino areaDestino, Boolean visibleWeb) {
    public record Referencia(@NotNull @Min(1) Integer id) { }
    public Producto toEntity() {
        var p = new Producto(); p.setVersion(version);
        var c = new Categoria(); c.setId(categoria.id()); p.setCategoria(c);
        if (marca != null) { var m = new Marca(); m.setId(marca.id()); p.setMarca(m); }
        var supplier = new Proveedor(); supplier.setId(proveedor.id()); p.setProveedor(supplier);
        p.setCodigoBarras(codigoBarras.trim()); p.setNombre(nombre.trim()); p.setDescripcion(descripcion);
        p.setPrecioCompra(precioCompra); p.setPrecioVenta(precioVenta);
        p.setStockActual(stockActual); p.setStockMinimo(stockMinimo);
        p.setAreaDestino(areaDestino == null ? AreaDestino.COCINA : areaDestino); p.setVisibleWeb(Boolean.TRUE.equals(visibleWeb));
        return p;
    }
}
