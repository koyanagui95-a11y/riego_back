package com.sistema.sistema_riego_inteligente.servicios;

import com.sistema.sistema_riego_inteligente.Entidad.PushSubscription;
import com.sistema.sistema_riego_inteligente.Repositorios.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Security;
import java.util.List;

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

    @PostConstruct
    public void init() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        this.pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
    }

    public void enviarNotificacion(Long usuarioId, String titulo, String cuerpo) {
        List<PushSubscription> subs = subscriptionRepo.findByUsuarioId(usuarioId);
        for (PushSubscription sub : subs) {
            try {
                Notification notification = new Notification(
                        sub.getEndpoint(),
                        sub.getP256dh(),
                        sub.getAuth(),
                        "{\"title\":\"" + titulo + "\",\"body\":\"" + cuerpo + "\"}"
                );
                pushService.send(notification);
            } catch (Exception e) {
                System.err.println("[PUSH] Error enviando a " + sub.getEndpoint() + ": " + e.getMessage());
            }
        }
    }

    public String getVapidPublicKeyBase64() {
        return vapidPublicKey;
    }
}
