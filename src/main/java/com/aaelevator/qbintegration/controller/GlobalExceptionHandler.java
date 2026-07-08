package com.aaelevator.qbintegration.controller;

import com.aaelevator.qbintegration.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.eclipse.angus.mail.iap.ConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final AuditService auditService;

    public GlobalExceptionHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Fallo de conexión con la BD (túnel SSH caído, MariaDB no responde).
     * Cubre tanto qb_integration_db como wordpress_db.
     */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<Map<String, Object>> handleDbConnectionFailure(
            DataAccessResourceFailureException ex,
            HttpServletRequest request) {

        String rootMsg = rootCause(ex).toLowerCase();
        String dbIdentifier;

        if (rootMsg.contains("3307") || rootMsg.contains("wordpress")) {
            dbIdentifier = "Tickets database (cloud)";
        } else if (rootMsg.contains("3306") || rootMsg.contains("qb_integration")) {
            dbIdentifier = "Accounting database (cloud)";
        } else {
            dbIdentifier = "Database";
        }

        log.error("DB connection failure [{}] on [{}]: {}", dbIdentifier, request.getRequestURI(), ex.getMessage());
        auditService.log("DB_CONNECTION_ERROR", extractEmail(request), request,
                dbIdentifier + " unreachable: " + rootCause(ex));

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "Service temporarily unavailable",
                "detail", dbIdentifier + " connection failed. Please try again in a few moments.",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Error general de acceso a datos (query inválida, constraint violation, etc.)
     * Solo alcanza aquí si no fue capturado antes en el servicio.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccessException(DataAccessException ex, HttpServletRequest request) {

        log.error("DB connection failure on [{}]: {}", request.getRequestURI(), ex.getMessage());
        auditService.log("DB_ERROR", extractEmail(request), request, "Data access error: " + rootCause(ex));

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error",
                "detail", "A data error ocurred. Please contact support if this persists.", "timestamp", LocalDateTime.now().toString()));
    }

    /**
     * Fallo de conectividad de red genérico (SMTP caído, endpoint externo no responde).
     */
    @ExceptionHandler(ConnectionException.class)
    public ResponseEntity<Map<String, Object>> handleConnectionException(ConnectionException ex, HttpServletRequest request) {
        log.error("Network connection failure on [{}]: {}", request.getRequestURI(), ex.getMessage());
        auditService.log("NETWORK_ERROR", extractEmail(request), request, "Connection refused: " + ex.getMessage());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "Service temporarily unavailable",
                "detail", "A required service is unreachable. Please try again shortly", "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Parámetro de la URL con tipo incorrecto (ej. months=abc en vez de un entero).
     * Es un error de entrada del cliente (400), no una falla del servidor (500).
     * Sin este handler, MethodArgumentTypeMismatchException caía en el fallback
     * genérico de abajo y se reportaba incorrectamente como 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        log.warn("Invalid parameter type on [{}]: {} = '{}'", request.getRequestURI(), ex.getName(), ex.getValue());
        auditService.log("BAD_REQUEST", extractEmail(request), request,
                "Invalid value for parameter '" + ex.getName() + "'");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Bad request",
                "detail", "Invalid value for parameter '" + ex.getName() + "'.",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
    /**
     * Violación de restricciones de validación (@Min, @Max, etc.) aplicadas
     * directamente sobre @RequestParam en un controlador anotado con @Validated.
     * Ej: months=999999999 excediendo el límite superior configurado.
     *
     * NOTA: en Spring Framework 6.1+ (usado por Spring Boot 4.0.5), la
     * validación nativa de parámetros de método lanza HandlerMethodValidationException,
     * no jakarta.validation.ConstraintViolationException directamente. Se
     * mantienen ambos handlers por robustez ante configuraciones distintas.
     */
    @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleMethodValidation(
            org.springframework.web.method.annotation.HandlerMethodValidationException ex, HttpServletRequest request) {

        log.warn("Validation failed on [{}]: {}", request.getRequestURI(), ex.getMessage());
        auditService.log("BAD_REQUEST", extractEmail(request), request,
                "Validation failed: " + ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Bad request",
                "detail", "Invalid request parameters.",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        log.warn("Validation failed on [{}]: {}", request.getRequestURI(), ex.getMessage());
        auditService.log("BAD_REQUEST", extractEmail(request), request,
                "Validation failed: " + ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Bad request",
                "detail", "Invalid request parameters.",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Fallback para cualquier excepción no capturada por los handlers anteriores.
     * Evita que stack traces internos lleguen al cliente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception on [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        auditService.log("INTERNAL_ERROR", extractEmail(request), request, ex.getClass().getSimpleName() + ": " + ex.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error",
                "detail", "An unexpected error ocurred.", "timestamp", LocalDateTime.now().toString()));
    }

    // ---helpers ?? ----

    private String extractEmail(HttpServletRequest request) {
        // El email ya esta puesto en el SecurityContext por JwtAuthenticationFilter
        // Se recupera del principal si está disponible
        if (request.getUserPrincipal() != null) {
            return request.getUserPrincipal().getName();
        }
        return null;
    }

    private String rootCause(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() +": "+ cause.getMessage();
    }
}