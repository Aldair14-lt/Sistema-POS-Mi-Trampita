package MiTrampita.SistemaPOS.config;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> staleUpdate(Exception exception) {
        return error(HttpStatus.CONFLICT, "El registro cambió mientras lo editabas. Recarga los datos antes de guardar.");
    }
    @ExceptionHandler(MiTrampita.SistemaPOS.exception.StockInsuficienteException.class)
    public ResponseEntity<ApiError> stock(MiTrampita.SistemaPOS.exception.StockInsuficienteException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    public ResponseEntity<ApiError> concurrentUpdate(Exception exception) {
        return error(HttpStatus.CONFLICT, "Otro usuario está actualizando estos datos. Actualiza e inténtalo de nuevo.");
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .distinct()
            .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, message.isBlank() ? "Datos inválidos" : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraint(ConstraintViolationException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformed(HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflict(DataIntegrityViolationException exception) {
        return error(HttpStatus.CONFLICT, "El registro duplica un dato existente o tiene relaciones inválidas");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(ResponseStatusException exception) {
        if (exception.getStatusCode().value() == 429)
            return ResponseEntity.status(429).header("Retry-After", "60")
                .body(new ApiError(429, exception.getReason()));
        return error(exception.getStatusCode(), exception.getReason() == null ? "Solicitud inválida" : exception.getReason());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message));
    }

    private ResponseEntity<ApiError> error(org.springframework.http.HttpStatusCode status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message));
    }

    public record ApiError(int status, String message) { }
}
