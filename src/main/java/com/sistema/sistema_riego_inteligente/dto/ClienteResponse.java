package com.sistema.sistema_riego_inteligente.dto;

import com.sistema.sistema_riego_inteligente.Entidad.Cliente;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Vista de un cliente para el panel admin. Mantiene la misma forma JSON que la entidad
 * (incluido el objeto anidado "usuario"), pero sin proxies de Hibernate ni la contraseña.
 */
public record ClienteResponse(
        Long id,
        UsuarioResumen usuario,
        String nombres,
        String apellidos,
        String correoPersonal,
        String telefono,
        String estado,
        LocalDateTime fechaRegistro,
        LocalDateTime fechaActivacion,
        String dispositivoId,
        String descripcionServicio,
        Double costoSistema,
        Double costoSensores,
        Double costoComponentes,
        Double costoMensual,
        LocalDateTime fechaProximoPago,
        Integer diasGracia,
        /** Días hasta el próximo pago (negativo = vencido). null si no hay fecha. */
        Long diasParaVencimiento
) {
    public record UsuarioResumen(Long id, String username, String email, String rol) {}

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    public static ClienteResponse de(Cliente c) {
        var u = c.getUsuario();
        Long dias = c.getFechaProximoPago() == null ? null
                : ChronoUnit.DAYS.between(LocalDateTime.now(ZONA_LIMA), c.getFechaProximoPago());
        return new ClienteResponse(
                c.getId(),
                u == null ? null : new UsuarioResumen(u.getId(), u.getUsername(), u.getEmail(), u.getRol()),
                c.getNombres(),
                c.getApellidos(),
                c.getCorreoPersonal(),
                c.getTelefono(),
                c.getEstado(),
                c.getFechaRegistro(),
                c.getFechaActivacion(),
                c.getDispositivoId(),
                c.getDescripcionServicio(),
                c.getCostoSistema(),
                c.getCostoSensores(),
                c.getCostoComponentes(),
                c.getCostoMensual(),
                c.getFechaProximoPago(),
                c.getDiasGracia(),
                dias
        );
    }
}
