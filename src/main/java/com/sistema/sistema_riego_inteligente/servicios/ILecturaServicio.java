package com.sistema.sistema_riego_inteligente.servicios;

import com.sistema.sistema_riego_inteligente.Entidad.Lectura;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ILecturaServicio {

    Lectura guardar(Lectura nuevaLectura);
    Page<Lectura> obtenerTodo(Pageable pageable);
    Page<Lectura> obtenerPorUsuarioId(Long usuarioId, Pageable pageable);
    Lectura obtenerPorId(Long id);
    Lectura obtenerUltimoEstado();
    Lectura obtenerUltimoEstadoPorUsuario(Long usuarioId);
    void eliminar(Long id);

    void activarManual(Long usuarioId);
    void desactivarManual(Long usuarioId);
    boolean estaActivaManual(Long usuarioId);
    boolean hayAlertaFlujo(Long usuarioId);
    void resetAllManual();

    void registrarContactoEsp32(Long usuarioId);
    long obtenerUltimoContactoPorUsuario(Long usuarioId);

    void solicitarCambioWifi(Long usuarioId, String ssid, String password);
    Map<String, String> consumirCambioWifi(Long usuarioId);
    void confirmarCambioWifi(Long usuarioId);
}