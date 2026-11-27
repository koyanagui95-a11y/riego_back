package com.sistema.sistema_riego_inteligente.dto;

import com.sistema.sistema_riego_inteligente.Entidad.Lectura;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Respuesta pública de una lectura. Mismos nombres de campo que antes, pero SIN el objeto
 * "usuario" (que exponía email y rol del dueño en cada fila).
 */
public record LecturaResponse(
        Long id,
        Integer humedad_suelo,
        Double humedad_ambiente,
        Double temperatura,
        Double flujoAgua,
        Boolean bombaActiva,
        Map<String, Object> extras,
        LocalDateTime fechaRegistro
) {
    public static LecturaResponse de(Lectura l) {
        return new LecturaResponse(
                l.getId(),
                l.getHumedad_suelo(),
                l.getHumedad_ambiente(),
                l.getTemperatura(),
                l.getFlujoAgua(),
                l.getBombaActiva(),
                l.getExtras(),
                l.getFechaRegistro()
        );
    }
}
