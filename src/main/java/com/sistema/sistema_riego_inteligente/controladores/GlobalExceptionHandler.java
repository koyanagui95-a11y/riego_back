package com.sistema.sistema_riego_inteligente.controladores;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Respuestas de error uniformes: siempre {"mensaje": "..."} (el mismo formato que ya usan los controladores).
 * No hay un handler genérico de Exception a propósito: los 404/405 de Spring y los errores reales
 * (500) siguen su curso normal.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errores.putIfAbsent(fe.getField(), fe.getDefaultMessage()));

        String primero = errores.entrySet().stream().findFirst()
                .map(e -> e.getValue() != null ? e.getValue() : "Valor inválido en '" + e.getKey() + "'.")
                .orElse("Datos inválidos.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mensaje", primero);
        body.put("errores", errores);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> cuerpoIlegible(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "Cuerpo de la petición inválido o mal formado."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> parametroFaltante(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "Falta el parámetro obligatorio '" + ex.getParameterName() + "'."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> estado(ResponseStatusException ex) {
        String razon = ex.getReason() != null ? ex.getReason() : "Error en la petición.";
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("mensaje", razon));
    }

    /** @PreAuthorize lanza AccessDeniedException dentro del controlador: sin esto terminaría en 500. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> accesoDenegado(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("mensaje", "No tienes permiso para realizar esta acción."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integridad(DataIntegrityViolationException ex) {
        log.warn("[BD] Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", "El registro ya existe o está siendo usado por otro dato."));
    }
}
