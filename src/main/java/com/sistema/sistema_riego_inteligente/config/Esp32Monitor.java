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

    @Autowired private WebPushService webPushService;
    @Autowired private ClienteRepository clienteRepositorio;
    @Autowired private ILecturaServicio lecturaServicio;

    private static final long UMBRAL_ONLINE_MS = 20000L;
    private static final long INTERVALO_RECORDATORIO_MS = 3600000L;

    private final Map<Long, Long> inicioDesconexion = new ConcurrentHashMap<>();
    private final Map<Long, Long> ultimaNotificacionOffline = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 15000)
    public void verificarConexionEsp32() {
        long ahora = System.currentTimeMillis();
        clienteRepositorio.findAll().forEach(cliente -> {
            if (cliente.getUsuario() == null) return;
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
                            ? " Lleva más de 1 hora sin conexión." : "";

                    boolean enviado = webPushService.enviarNotificacion(
                            usuarioId,
                            "ESP32 desconectado",
                            cliente.getNombres() + ", tu dispositivo no tiene conexión." + detalleHoras
                                    + " El sistema operará localmente.",
                            "esp32-offline"
                    );
                    // ⚠️ Solo marcar como enviado si REALMENTE se entregó.
                    // Así, si falla la red, se reintenta en el próximo ciclo (15s) y no en 1 hora.
                    if (enviado) {
                        ultimaNotificacionOffline.put(usuarioId, ahora);
                    } else {
                        System.err.println("[MONITOR] Notificación ESP32 offline NO entregada para "
                                + usuarioId + ". Se reintentará en 15s.");
                    }
                }
            } else {
                inicioDesconexion.remove(usuarioId);
                ultimaNotificacionOffline.remove(usuarioId);
            }
        });
    }
}