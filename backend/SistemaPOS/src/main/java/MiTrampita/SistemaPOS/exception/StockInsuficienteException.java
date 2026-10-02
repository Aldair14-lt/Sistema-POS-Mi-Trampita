package MiTrampita.SistemaPOS.exception;

/** RuntimeException: Spring revierte todo el cobro, incluido cualquier descuento previo. */
public class StockInsuficienteException extends RuntimeException {
    public StockInsuficienteException(String producto, int disponible, int solicitado) {
        super("Stock insuficiente para " + producto + ". Disponible: " + disponible + "; solicitado: " + solicitado);
    }
}