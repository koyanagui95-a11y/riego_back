package com.sistema.sistema_riego_inteligente.dto;

import jakarta.validation.constraints.Size;

/** Cuerpo opcional de la activación. Si password es null se genera una temporal aleatoria. */
public record ActivarClienteRequest(
        @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres") String password
) {}
