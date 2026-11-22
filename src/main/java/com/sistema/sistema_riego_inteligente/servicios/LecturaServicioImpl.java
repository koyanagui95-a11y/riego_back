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
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LecturaServicioImpl implements ILecturaServicio {

    @Autowired
    private LecturaRepository lecturaRepositorio;

    @Autowired
    private ClienteRepository clienteRepositorio;

    @Autowired
    private WebPushService webPushService;

    private static final long DURACION_MANUAL_MAXIMA_MS = 5 * 60 * 1000;

    private boolean bombaManual = false;
    private long inicioBombaManual = 0;
    private volatile long ultimoContactoEsp32 = 0;

    private final Map<Long, Map<String, String>> cambioWifiPendiente = new ConcurrentHashMap<>();
    private final Map<Long, Long> ultimaNotificacionTanqueBajo = new ConcurrentHashMap<>();

    @Override
    public Lectura guardar(Lectura nuevaLectura) {
        Lectura ultimaLectura = null;
        if (nuevaLectura.getUsuario() != null) {
            ultimaLectura = lecturaRepositorio
                    .findTopByUsuarioIdOrderByIdDesc(nuevaLectura.getUsuario().getId())
                    .orElse(null);
        }

        if (ultimaLectura == null) {
            return lecturaRepositorio.save(nuevaLectura);
        }

        boolean bombaNueva = Boolean.TRUE.equals(nuevaLectura.getBombaActiva());
        boolean bombaUltima = Boolean.TRUE.equals(ultimaLectura.getBombaActiva());
        boolean cambioBomba = bombaNueva != bombaUltima;

        int humedadNueva = nuevaLectura.getHumedad_suelo();
        int humedadUltima = ultimaLectura.getHumedad_suelo();
        boolean cambioHumedadSignificativo = Math.abs(humedadNueva - humedadUltima) >= 15;

        boolean tiempoExcedido = false;
        if (ultimaLectura.getFechaRegistro() != null) {
            long segundos = Duration.between(
                    ultimaLectura.getFechaRegistro(),
                    LocalDateTime.now(ZoneId.of("America/Lima"))
            ).getSeconds();
            tiempoExcedido = segundos >= 300;
        }

        Long usuarioId = nuevaLectura.getUsuario().getId();
        String nombreCliente = obtenerNombreCliente(usuarioId);

        if (humedadNueva < 40 && humedadUltima >= 40 && nuevaLectura.getUsuario() != null) {
            try {
                webPushService.enviarNotificacion(
                        usuarioId,
                        "¡Humedad baja!",
                        nombreCliente + ", la humedad del suelo es del " + humedadNueva + "%. Iniciando riego automático."
                );
            } catch (Exception e) {
                System.err.println("[PUSH] Error enviando notificación: " + e.getMessage());
            }
        }

        int tanqueNuevo = obtenerTanque(nuevaLectura);
        int tanqueUltimo = obtenerTanque(ultimaLectura);

        if (tanqueNuevo == 0) {
            long ahora = System.currentTimeMillis();
            Long ultimaNotif = ultimaNotificacionTanqueBajo.get(usuarioId);
            if (ultimaNotif == null || (ahora - ultimaNotif) > 3600000) {
                try {
                    webPushService.enviarNotificacion(
                            usuarioId,
                            "Tanque bajo",
                            nombreCliente + ", el tanque está bajo. Por favor llena el tanque."
                    );
                    ultimaNotificacionTanqueBajo.put(usuarioId, ahora);
                } catch (Exception e) {
                    System.err.println("[PUSH] Error enviando notificación de tanque: " + e.getMessage());
                }
            }

            if (bombaUltima && !bombaNueva && tanqueNuevo == 0) {
                try {
                    webPushService.enviarNotificacion(
                            usuarioId,
                            "Riego detenido",
                            nombreCliente + ", el riego se detuvo por monitoreo de tanque bajo."
                    );
                } catch (Exception e) {
                    System.err.println("[PUSH] Error enviando notificación de riego detenido: " + e.getMessage());
                }
            }
        } else if (tanqueNuevo == 1) {
            ultimaNotificacionTanqueBajo.remove(usuarioId);
        }

        if (cambioBomba || cambioHumedadSignificativo || tiempoExcedido) {
            return lecturaRepositorio.save(nuevaLectura);
        }
        return nuevaLectura;
    }

    private String obtenerNombreCliente(Long usuarioId) {
        Optional<Cliente> clienteOpt = clienteRepositorio.findByUsuarioId(usuarioId);
        if (clienteOpt.isPresent()) {
            return clienteOpt.get().getNombres();
        }
        return "Cliente";
    }

    private int obtenerTanque(Lectura lectura) {
        if (lectura.getExtras() != null && lectura.getExtras().containsKey("tanque")) {
            Object valor = lectura.getExtras().get("tanque");
            if (valor instanceof Number) {
                return ((Number) valor).intValue();
            }
        }
        return -1;
    }

    @Override
    public Page<Lectura> obtenerTodo(Pageable pageable) {
        return lecturaRepositorio.findAll(pageable);
    }

    @Override
    public Page<Lectura> obtenerPorUsuarioId(Long usuarioId, Pageable pageable) {
        return lecturaRepositorio.findByUsuarioId(usuarioId, pageable);
    }

    @Override
    public Lectura obtenerPorId(Long id) {
        return lecturaRepositorio.findById(id).orElse(null);
    }

    @Override
    public Lectura obtenerUltimoEstado() {
        return lecturaRepositorio.findTopByOrderByIdDesc().orElse(null);
    }

    @Override
    public Lectura obtenerUltimoEstadoPorUsuario(Long usuarioId) {
        return lecturaRepositorio.findTopByUsuarioIdOrderByIdDesc(usuarioId).orElse(null);
    }

    @Override
    public void eliminar(Long id) {
        lecturaRepositorio.deleteById(id);
    }

    @Override
    public void activarManual() {
        bombaManual = true;
        inicioBombaManual = System.currentTimeMillis();
    }

    @Override
    public void desactivarManual() {
        bombaManual = false;
        inicioBombaManual = 0;
    }

    @Override
    public boolean estaActivaManual() {
        if (bombaManual && inicioBombaManual > 0) {
            long transcurrido = System.currentTimeMillis() - inicioBombaManual;
            if (transcurrido >= DURACION_MANUAL_MAXIMA_MS) {
                bombaManual = false;
                inicioBombaManual = 0;
            }
        }
        return bombaManual;
    }

    @Override
    public void registrarContactoEsp32() {
        ultimoContactoEsp32 = System.currentTimeMillis();
    }

    @Override
    public long obtenerUltimoContactoEsp32() {
        return ultimoContactoEsp32;
    }

    @Override
    public void solicitarCambioWifi(Long usuarioId, String ssid, String password) {
        cambioWifiPendiente.put(usuarioId, Map.of(
                "ssid", ssid,
                "password", password
        ));
    }

    @Override
    public Map<String, String> consumirCambioWifi(Long usuarioId) {
        return cambioWifiPendiente.remove(usuarioId);
    }

    @Override
    public void confirmarCambioWifi(Long usuarioId) {
        cambioWifiPendiente.remove(usuarioId);
    }
}