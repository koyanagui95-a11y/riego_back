package com.sistema.sistema_riego_inteligente.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sistema.sistema_riego_inteligente.Entidad.Lectura;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashMap;
import java.util.Map;

/**
 * Lo que envía el ESP32 en POST /api/v1/guardar/{usuarioId}.
 * Los nombres de campo son exactamente los del JSON actual del firmware.
 * Antes se recibía la entidad JPA directamente: @Valid no validaba nada y un campo ausente
 * provocaba NullPointerException (HTTP 500) al des-boxear.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LecturaRequest(
        @NotNull(message = "humedad_suelo es obligatorio")
        @Min(value = 0, message = "humedad_suelo debe estar entre 0 y 100")
        @Max(value = 100, message = "humedad_suelo debe estar entre 0 y 100")
        Integer humedad_suelo,

        @NotNull(message = "humedad_ambiente es obligatorio")
        @DecimalMin(value = "0.0", message = "humedad_ambiente debe estar entre 0 y 100")
        @DecimalMax(value = "100.0", message = "humedad_ambiente debe estar entre 0 y 100")
        Double humedad_ambiente,

        @NotNull(message = "temperatura es obligatoria")
        @DecimalMin(value = "-40.0", message = "temperatura fuera de rango (-40 a 125)")
        @DecimalMax(value = "125.0", message = "temperatura fuera de rango (-40 a 125)")
        Double temperatura,

        @NotNull(message = "flujoAgua es obligatorio")
        @DecimalMin(value = "0.0", message = "flujoAgua no puede ser negativo")
        @DecimalMax(value = "500.0", message = "flujoAgua fuera de rango")
        Double flujoAgua,

        @NotNull(message = "bombaActiva es obligatorio")
        Boolean bombaActiva,

        @Size(max = 20, message = "extras admite máximo 20 claves")
        Map<String, Object> extras
) {
    public Lectura aEntidad() {
        Lectura l = new Lectura();
        l.setHumedad_suelo(humedad_suelo);
        l.setHumedad_ambiente(humedad_ambiente);
        l.setTemperatura(temperatura);
        l.setFlujoAgua(flujoAgua);
        l.setBombaActiva(bombaActiva);
        l.setExtras(extras != null ? new HashMap<>(extras) : new HashMap<>());
        return l;
    }
}
