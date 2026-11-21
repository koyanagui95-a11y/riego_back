package com.sistema.sistema_riego_inteligente.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroClienteRequest(
        @NotBlank @Size(max = 100) String nombres,
        @NotBlank @Size(max = 100) String apellidos,
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@gmail\\.com$", message = "Correo personal inválido")
        String correoPersonal,
        String telefono,
        @NotBlank @Size(min = 8, max = 100) String password,
        String descripcionServicio,
        Double costoSistema,
        Double costoSensores,
        Double costoComponentes,
        Double costoMensual
) {}