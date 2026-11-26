package com.sistema.sistema_riego_inteligente.servicios;

import com.sistema.sistema_riego_inteligente.Entidad.Cliente;
import com.sistema.sistema_riego_inteligente.Entidad.Lectura;
import com.sistema.sistema_riego_inteligente.Repositorios.ClienteRepository;
import com.sistema.sistema_riego_inteligente.Repositorios.LecturaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LecturaServicioImpl implements ILecturaServicio {

    @Autowired private LecturaRepository lecturaRepositorio;
    @Autowired private ClienteRepository clienteRepositorio;
    @Autowired private WebPushService webPushService;

    private static final long DURACION_MANUAL_MAXIMA_MS = 5 * 60 * 1000;
    private static final long TIEMPO_MINIMO_ALERTA_FLUJO_MS = 5_000;    
    private static final double FLUJO_MINIMO_LPM = 0.05;                 
    private static final long DURACION_ALERTA_FLUJO_MS = 60_000;         
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private static class BombaState {
        volatile boolean activa;
        volatile long inicioActivacion;
        BombaState(boolean activa, long inicio) { this.activa = activa; this.inicioActivacion = inicio; }
    }

    private final Map<Long, BombaState> bombaPorUsuario = new ConcurrentHashMap<>();
    private final Map<Long, Long> alertaFlujoEmitidaEn = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, String>> cambioWifiPendiente = new ConcurrentHashMap<>();
    private final Map<Long, Long> ultimaNotificacionTanqueBajo = new ConcurrentHashMap<>();
    private final Map<Long, Long> ultimoContactoPorUsuario = new ConcurrentHashMap<>();

    @Override
    public Lectura guardar(Lectura nuevaLectura) {
        if (nuevaLectura.getFechaRegistro() == null) {
            nuevaLectura.setFechaRegistro(LocalDateTime.now(ZONA_LIMA));
        }

        Long usuarioId = nuevaLectura.getUsuario() != null ? nuevaLectura.getUsuario().getId() : null;

        if (usuarioId != null) {
            verificarFlujoBomba(usuarioId, nuevaLectura);
        }

        Lectura ultimaLectura = (usuarioId != null)
                ? lecturaRepositorio.findTopByUsuarioIdOrderByIdDesc(usuarioId).orElse(null)
                : null;

        if (ultimaLectura == null) {
            return lecturaRepositorio.save(nuevaLectura);
        }

        boolean bombaNueva = Boolean.TRUE.equals(nuevaLectura.getBombaActiva());
        boolean bombaUltima = Boolean.TRUE.equals(ultimaLectura.getBombaActiva());
        boolean cambioBomba = bombaNueva != bombaUltima;

        int humedadNueva = nuevaLectura.getHumedad_suelo();
        int humedadUltima = ultimaLectura.getHumedad_suelo();
        boolean cambioHumedadSignificativo = Math.abs(humedadNueva - humedadUltima) >= 15;

        int tanqueNuevo = obtenerTanque(nuevaLectura);
        int tanqueUltimo = obtenerTanque(ultimaLectura);
        boolean cambioTanque = (tanqueNuevo != -1 && tanqueUltimo != -1 && tanqueNuevo != tanqueUltimo);

        boolean tiempoExcedido = false;
        if (ultimaLectura.getFechaRegistro() != null) {
            long segundos = Duration.between(
                    ultimaLectura.getFechaRegistro(),
                    LocalDateTime.now(ZONA_LIMA)
            ).getSeconds();
            tiempoExcedido = segundos >= 720;
        }

        String nombreCliente = obtenerNombreCliente(usuarioId);

        if (humedadNueva < 40 && humedadUltima >= 40) {
            try {
                webPushService.enviarNotificacion(
                        usuarioId,
                        "¡Humedad baja!",
                        nombreCliente + ", la humedad del suelo es del " + humedadNueva + "%. Iniciando riego automático.",
                        "humedad-baja"
                );
            } catch (Exception e) {
                System.err.println("[PUSH] Error humedad: " + e.getMessage());
            }
        }

        if (tanqueNuevo == 0 && tanqueUltimo == 0) {
            long ahora = System.currentTimeMillis();
            Long ultimaNotif = ultimaNotificacionTanqueBajo.get(usuarioId);
            if (ultimaNotif == null || (ahora - ultimaNotif) > 3_600_000L) {
                boolean ok = webPushService.enviarNotificacion(
                        usuarioId,
                        "Tanque bajo",
                        nombreCliente + ", el tanque está bajo. Por favor llena el tanque.",
                        "tanque-bajo"
                );
                if (ok) ultimaNotificacionTanqueBajo.put(usuarioId, ahora);
            }
            if (bombaUltima && !bombaNueva) {
                try {
                    webPushService.enviarNotificacion(
                            usuarioId,
                            "Riego detenido",
                            nombreCliente + ", el riego se detuvo por monitoreo de tanque bajo.",
                            "riego-detenido"
                    );
                } catch (Exception e) {
                    System.err.println("[PUSH] Error riego detenido: " + e.getMessage());
                }
            }
        } else if (tanqueNuevo == 1) {
            ultimaNotificacionTanqueBajo.remove(usuarioId);
        }

        if (cambioBomba || cambioTanque || cambioHumedadSignificativo || tiempoExcedido) {
            return lecturaRepositorio.save(nuevaLectura);
        }
        return nuevaLectura;
    }

    private void verificarFlujoBomba(Long usuarioId, Lectura nuevaLectura) {
        BombaState estado = bombaPorUsuario.get(usuarioId);
        if (estado == null || !estado.activa) return;

        long transcurrido = System.currentTimeMillis() - estado.inicioActivacion;
        if (transcurrido < TIEMPO_MINIMO_ALERTA_FLUJO_MS) return;

        double flujo = nuevaLectura.getFlujoAgua() != null ? nuevaLectura.getFlujoAgua() : 0.0;
        if (flujo >= FLUJO_MINIMO_LPM) {
            alertaFlujoEmitidaEn.remove(usuarioId);
            return;
        }

        Long ultimaAlerta = alertaFlujoEmitidaEn.get(usuarioId);
        long ahora = System.currentTimeMillis();
        if (ultimaAlerta != null && (ahora - ultimaAlerta) < DURACION_ALERTA_FLUJO_MS) {
            return;
        }

        String nombreCliente = obtenerNombreCliente(usuarioId);
        boolean enviado = webPushService.enviarNotificacion(
                usuarioId,
                "Sin flujo de agua",
                nombreCliente + ", la bomba está encendida pero no sale agua. " +
                        "Revisa si la mini bomba tiene energía o si está obstruida.",
                "sin-flujo"
        );

        if (enviado) {
            estado.activa = false;
            estado.inicioActivacion = 0;
            alertaFlujoEmitidaEn.put(usuarioId, ahora);
            System.out.println("[FLUJO] Alerta enviada y bomba apagada para usuario " + usuarioId);
        }
    }

    private String obtenerNombreCliente(Long usuarioId) {
        return clienteRepositorio.findByUsuarioId(usuarioId)
                .map(Cliente::getNombres)
                .orElse("Cliente");
    }

    private int obtenerTanque(Lectura lectura) {
        if (lectura.getExtras() != null && lectura.getExtras().containsKey("tanque")) {
            Object valor = lectura.getExtras().get("tanque");
            if (valor instanceof Number) return ((Number) valor).intValue();
        }
        return -1;
    }

    @Override public Page<Lectura> obtenerTodo(Pageable p) { return lecturaRepositorio.findAll(p); }
    @Override public Page<Lectura> obtenerPorUsuarioId(Long id, Pageable p) { return lecturaRepositorio.findByUsuarioId(id, p); }
    @Override public Lectura obtenerPorId(Long id) { return lecturaRepositorio.findById(id).orElse(null); }
    @Override public Lectura obtenerUltimoEstado() { return lecturaRepositorio.findTopByOrderByIdDesc().orElse(null); }
    @Override public Lectura obtenerUltimoEstadoPorUsuario(Long id) { return lecturaRepositorio.findTopByUsuarioIdOrderByIdDesc(id).orElse(null); }
    @Override public void eliminar(Long id) { lecturaRepositorio.deleteById(id); }

    @Override
    public void activarManual(Long usuarioId) {
        bombaPorUsuario.put(usuarioId, new BombaState(true, System.currentTimeMillis()));
        alertaFlujoEmitidaEn.remove(usuarioId);   
    }

    @Override
    public void desactivarManual(Long usuarioId) {
        BombaState s = bombaPorUsuario.get(usuarioId);
        if (s != null) { s.activa = false; s.inicioActivacion = 0; }
        alertaFlujoEmitidaEn.remove(usuarioId);
    }

    @Override
    public boolean estaActivaManual(Long usuarioId) {
        BombaState s = bombaPorUsuario.get(usuarioId);
        if (s == null || !s.activa) return false;

        if (s.inicioActivacion > 0
                && (System.currentTimeMillis() - s.inicioActivacion) >= DURACION_MANUAL_MAXIMA_MS) {
            s.activa = false;
            s.inicioActivacion = 0;
            return false;
        }
        return true;
    }

    @Override
    public boolean hayAlertaFlujo(Long usuarioId) {
        Long t = alertaFlujoEmitidaEn.get(usuarioId);
        return t != null && (System.currentTimeMillis() - t) < DURACION_ALERTA_FLUJO_MS;
    }

    @Override public void registrarContactoEsp32(Long usuarioId) { ultimoContactoPorUsuario.put(usuarioId, System.currentTimeMillis()); }
    @Override public long obtenerUltimoContactoPorUsuario(Long usuarioId) { return ultimoContactoPorUsuario.getOrDefault(usuarioId, 0L); }

    @Override public void solicitarCambioWifi(Long usuarioId, String ssid, String password) {
        cambioWifiPendiente.put(usuarioId, Map.of("ssid", ssid, "password", password));
    }
    @Override public Map<String, String> consumirCambioWifi(Long usuarioId) { return cambioWifiPendiente.remove(usuarioId); }
    @Override public void confirmarCambioWifi(Long usuarioId) { cambioWifiPendiente.remove(usuarioId); }
        @Override
    public void resetAllManual() {
        bombaPorUsuario.clear();
        alertaFlujoEmitidaEn.clear();
        cambioWifiPendiente.clear();
        System.out.println("[StartupReset] Estado de bombas y WiFi reseteado.");
    }
}