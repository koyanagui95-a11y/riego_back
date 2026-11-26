
package com.sistema.sistema_riego_inteligente.servicios;

import com.sistema.sistema_riego_inteligente.Entidad.PushSubscription;
import com.sistema.sistema_riego_inteligente.Repositorios.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WebPushService {

    @Autowired
    private PushSubscriptionRepository subscriptionRepo;

    @Value("${app.vapid.public.key}")
    private String vapidPublicKey;

    @Value("${app.vapid.private.key}")
    private String vapidPrivateKey;

    @Value("${app.vapid.subject}")
    private String vapidSubject;

    private PushService pushService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_INTENTOS = 3;
    private static final long RETRY_BASE_MS = 800;
    private static final int TTL_SEGUNDOS = 86400;

    @PostConstruct
    public void init() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        this.pushService = new PushService(
                vapidPublicKey,
                vapidPrivateKey,
                vapidSubject
        );
    }

    public boolean enviarNotificacion(
            Long usuarioId,
            String titulo,
            String cuerpo,
            String tag
    ) {

        List<PushSubscription> subs =
                subscriptionRepo.findByUsuarioId(usuarioId);

        if (subs.isEmpty()) {
            System.out.println("[PUSH] Usuario " + usuarioId + " sin suscripciones");
            return false;
        }

        String payload;

        try {
            Map<String, Object> data = new HashMap<>();

            data.put("title", titulo);
            data.put("body", cuerpo);
            data.put("tag", tag != null ? tag : "riego");
            data.put("timestamp", System.currentTimeMillis());

            data.put(
                    "requireInteraction",
                    "sin-flujo".equals(tag) || "esp32-offline".equals(tag)
            );

            payload = objectMapper.writeValueAsString(data);

        } catch (Exception e) {
            System.err.println("[PUSH] Error serializando: " + e.getMessage());
            return false;
        }

        boolean algunExito = false;

        for (PushSubscription sub : subs) {
            if (enviarConReintentos(sub, payload)) {
                algunExito = true;
            }
        }

        return algunExito;
    }

    public boolean enviarNotificacion(
            Long usuarioId,
            String titulo,
            String cuerpo
    ) {
        return enviarNotificacion(usuarioId, titulo, cuerpo, "riego");
    }

    private boolean enviarConReintentos(
            PushSubscription sub,
            String payload
    ) {

        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {

            try {
                Notification notification = new Notification(
                        sub.getEndpoint(),
                        sub.getP256dh(),
                        sub.getAuth(),
                        payload.getBytes(StandardCharsets.UTF_8),
                        TTL_SEGUNDOS
                );

                HttpResponse response = pushService.send(notification);

                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode >= 200 && statusCode < 300) {
                    return true;
                }

                if (statusCode == 404 || statusCode == 410) {
                    eliminarSuscripcion(sub);
                    return false;
                }

                boolean retry = statusCode == 429
                        || statusCode == 500
                        || statusCode == 502
                        || statusCode == 503
                        || statusCode == 504;

                if (!retry || intento == MAX_INTENTOS) {
                    System.err.println(
                            "[PUSH] Fallo definitivo. HTTP: " + statusCode
                    );
                    return false;
                }

                esperarReintento(intento);

            } catch (Exception e) {

                String msg = e.getMessage() != null
                        ? e.getMessage().toLowerCase()
                        : "";

                if (msg.contains("410") || msg.contains("404")
                        || msg.contains("gone")
                        || msg.contains("invalidregistration")) {

                    eliminarSuscripcion(sub);
                    return false;
                }

                boolean retry = e instanceof IOException
                        || msg.contains("429")
                        || msg.contains("500")
                        || msg.contains("502")
                        || msg.contains("503")
                        || msg.contains("504")
                        || msg.contains("timeout")
                        || msg.contains("connect");

                if (!retry || intento == MAX_INTENTOS) {
                    System.err.println(
                            "[PUSH] Fallo definitivo (intento "
                                    + intento + "): " + msg
                    );
                    return false;
                }

                esperarReintento(intento);
            }
        }

        return false;
    }

    private void eliminarSuscripcion(PushSubscription sub) {

        System.err.println(
                "[PUSH] Suscripción inválida, eliminando: "
                        + sub.getEndpoint()
        );

        try {
            subscriptionRepo.deleteById(sub.getId());
        } catch (Exception e) {
            System.err.println(
                    "[PUSH] Error eliminando suscripción: " + e.getMessage()
            );
        }
    }

    private void esperarReintento(int intento) {

        long delay = RETRY_BASE_MS * (long) Math.pow(2, intento - 1);

        System.err.println(
                "[PUSH] Reintentando en " + delay + " ms"
        );

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Proceso de reintento interrumpido", e
            );
        }
    }

    public String getVapidPublicKeyBase64() {
        return vapidPublicKey;
    }
}
