package com.sistema.sistema_riego_inteligente.config;

import com.sistema.sistema_riego_inteligente.Repositorios.ClienteRepository;
import com.sistema.sistema_riego_inteligente.servicios.ILecturaServicio;
import com.sistema.sistema_riego_inteligente.servicios.WebPushService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class Esp32Monitor {

    @Autowired
    private WebPushService webPushService;
    @Autowired
    private ClienteRepository clienteRepositorio;
    @Autowired
    private ILecturaServicio lecturaServicio;

    private static final long UMBRAL_ONLINE_MS = 30000L;
    private static final long INTERVALO_RECORDATORIO_MS = 3600000L;
    private static final long GRACIA_ARRANQUE_MS = 45000L;

    private final long tiempoInicioServidor = System.currentTimeMillis();
    private final Map<Long, Long> inicioDesconexion = new ConcurrentHashMap<>();
    private final Map<Long, Long> ultimaNotificacionOffline = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> notificacionOfflineEnviada = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 15000)
    public void verificarConexionEsp32() {
        long ahora = System.currentTimeMillis();
        if (ahora - tiempoInicioServidor < GRACIA_ARRANQUE_MS) {
            return;
        }

        clienteRepositorio.findAll().forEach(cliente -> {
            if (cliente.getUsuario() == null) {
                return;
            }
            Long usuarioId = cliente.getUsuario().getId();
            long ultimoContacto = lecturaServicio.obtenerUltimoContactoPorUsuario(usuarioId);
            boolean online = ultimoContacto > 0 && (ahora - ultimoContacto) < UMBRAL_ONLINE_MS;

            if (!online) {
                inicioDesconexion.computeIfAbsent(usuarioId, k -> ahora);
                Long ultimaNotif = ultimaNotificacionOffline.get(usuarioId);

                if (ultimaNotif == null || (ahora - ultimaNotif) >= INTERVALO_RECORDATORIO_MS) {
                    long desconectadoDesde = inicioDesconexion.get(usuarioId);
                    long tiempoSinConexion = ahora - desconectadoDesde;
                    String detalleHoras = tiempoSinConexion >= INTERVALO_RECORDATORIO_MS
                            ? " Lleva más de 1 hora sin responder." : "";

                    boolean enviado = webPushService.enviarNotificacion(
                            usuarioId,
                            "Dispositivo sin conexión",
                            cliente.getNombres() + ", se perdió la comunicación con tu equipo de riego." + detalleHoras
                            + " Verifica si tiene energía eléctrica o conexión WiFi (si tiene energía, seguirá operando de forma automática local).",
                            "esp32-offline"
                    );

                    if (enviado) {
                        ultimaNotificacionOffline.put(usuarioId, ahora);
                        notificacionOfflineEnviada.put(usuarioId, true);
                    }
                }
            } else {
                if (Boolean.TRUE.equals(notificacionOfflineEnviada.get(usuarioId))) {
                    webPushService.enviarNotificacionAsync(
                            usuarioId,
                            "Conexión restablecida",
                            cliente.getNombres() + ", tu equipo de riego volvió a conectarse y está en línea.",
                            "esp32-online"
                    );
                }
                inicioDesconexion.remove(usuarioId);
                ultimaNotificacionOffline.remove(usuarioId);
                notificacionOfflineEnviada.remove(usuarioId);
            }
        });
    }
}
