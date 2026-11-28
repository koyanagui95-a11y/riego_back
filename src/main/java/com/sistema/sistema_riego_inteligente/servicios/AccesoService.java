package com.sistema.sistema_riego_inteligente.servicios;

import com.sistema.sistema_riego_inteligente.Repositorios.ClienteRepository;
import com.sistema.sistema_riego_inteligente.config.UsuarioAutenticado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Centraliza las reglas de "¿quién puede tocar los datos de este usuarioId?".
 *
 * - Un CLIENTE solo accede a su propio usuarioId.
 * - ADMIN y PERSONAL acceden a cualquiera.
 * - El ESP32 no usa JWT: se identifica con la cabecera X-Device-Id (el dispositivoId UUID del cliente).
 *
 * Modo compatibilidad: mientras app.device.auth.required=false, las llamadas SIN sesión y SIN
 * X-Device-Id a los endpoints del ESP32 se siguen aceptando (como hasta ahora). Cuando el firmware
 * ya envíe X-Device-Id, poner app.device.auth.required=true.
 */
@Service
public class AccesoService {

    public enum Origen { USUARIO, DISPOSITIVO }

    private static final Logger log = LoggerFactory.getLogger(AccesoService.class);

    private final ClienteRepository clienteRepositorio;
    private final boolean dispositivoObligatorio;

    public AccesoService(ClienteRepository clienteRepositorio,
                         @Value("${app.device.auth.required:false}") boolean dispositivoObligatorio) {
        this.clienteRepositorio = clienteRepositorio;
        this.dispositivoObligatorio = dispositivoObligatorio;
        if (!dispositivoObligatorio) {
            log.warn("[ACCESO] app.device.auth.required=false: los endpoints del ESP32 aceptan llamadas sin "
                    + "X-Device-Id (modo compatibilidad). Actívalo cuando el firmware envíe la cabecera.");
        }
    }

    /** Usuario autenticado por JWT, o null si la petición es anónima. */
    public UsuarioAutenticado usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof UsuarioAutenticado u) ? u : null;
    }

    /** Para endpoints de uso exclusivo del frontend. */
    public void exigirPropietarioOStaff(Long usuarioId) {
        UsuarioAutenticado u = usuarioActual();
        if (u == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado o sesión expirada.");
        }
        verificarPropiedad(u, usuarioId);
    }

    /** Para endpoints que usan tanto el frontend (JWT) como el ESP32 (X-Device-Id). */
    public Origen autorizarUsuarioODispositivo(Long usuarioId, String dispositivoId) {
        UsuarioAutenticado u = usuarioActual();
        if (u != null) {
            verificarPropiedad(u, usuarioId);
            return Origen.USUARIO;
        }

        if (dispositivoId != null && !dispositivoId.isBlank()) {
            if (dispositivoCoincide(usuarioId, dispositivoId.trim())) {
                return Origen.DISPOSITIVO;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Dispositivo no autorizado para este usuario.");
        }

        if (!dispositivoObligatorio) {
            return Origen.DISPOSITIVO; // modo compatibilidad
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Se requiere sesión o la cabecera X-Device-Id.");
    }

    private void verificarPropiedad(UsuarioAutenticado u, Long usuarioId) {
        if (u.esStaff() || u.id().equals(usuarioId)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a este recurso.");
    }

    private boolean dispositivoCoincide(Long usuarioId, String dispositivoId) {
        return clienteRepositorio.findByUsuarioId(usuarioId)
                .map(c -> c.getDispositivoId())
                .map(real -> MessageDigest.isEqual(
                        real.getBytes(StandardCharsets.UTF_8),
                        dispositivoId.getBytes(StandardCharsets.UTF_8)))
                .orElse(false);
    }
}
