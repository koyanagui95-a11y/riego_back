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

    private final Map<Long, Long> ultimaNotificacionOffline = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 60000) // Cada minuto
    public void verificarConexionEsp32() {
        long ahora = System.currentTimeMillis();
        clienteRepositorio.findAll().forEach(cliente -> {
            Long usuarioId = cliente.getUsuario().getId();
            long ultimoContacto = lecturaServicio.obtenerUltimoContactoPorUsuario(usuarioId);
            boolean online = (ahora - ultimoContacto) < 120000; // 2 minutos

            if (!online) {
                long ultimaNotif = ultimaNotificacionOffline.getOrDefault(usuarioId, 0L);
                if (ahora - ultimaNotif > 3600000) { // 1 hora
                    webPushService.enviarNotificacion(
                            usuarioId,
                            "ESP32 desconectado",
                            cliente.getNombres() + ", tu dispositivo no tiene conexión. El sistema operará localmente."
                    );
                    ultimaNotificacionOffline.put(usuarioId, ahora);
                }
            } else {
                ultimaNotificacionOffline.remove(usuarioId);
            }
        });
    }
}