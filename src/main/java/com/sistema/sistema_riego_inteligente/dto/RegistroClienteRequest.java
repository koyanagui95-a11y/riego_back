package com.sistema.sistema_riego_inteligente.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RegistroClienteRequest(
        @NotBlank
        @Size(max = 100)
        String nombres,
        @NotBlank
        @Size(max = 100)
        String apellidos,
        @NotBlank
        @Size(max = 20)
        String genero,
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@gmail\\.com$", message = "Correo personal inválido")
        String correoPersonal,
        String telefono,
        @Size(max = 100)
        String password,
        @NotBlank
        @Size(max = 30)
        String tipoRiego,
        List<String> modalidadesRiego,
        String descripcionServicio,
        Double costoSistema,
        Double costoSensores,
        Double costoComponentes,
        Double costoMensual
        ) {}
