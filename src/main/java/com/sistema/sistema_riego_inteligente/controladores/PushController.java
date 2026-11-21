package com.sistema.sistema_riego_inteligente.controladores;

import com.sistema.sistema_riego_inteligente.Entidad.PushSubscription;
import com.sistema.sistema_riego_inteligente.Entidad.Usuario;
import com.sistema.sistema_riego_inteligente.Repositorios.PushSubscriptionRepository;
import com.sistema.sistema_riego_inteligente.Repositorios.UsuarioRepositorio;
import com.sistema.sistema_riego_inteligente.servicios.WebPushService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class PushController {

    @Autowired private PushSubscriptionRepository subscriptionRepo;
    @Autowired private UsuarioRepositorio usuarioRepositorio;
    @Autowired private WebPushService webPushService;

    @GetMapping("/vapid-public-key")
    public ResponseEntity<?> getVapidKey() {
        return ResponseEntity.ok(Map.of("publicKey", webPushService.getVapidPublicKeyBase64()));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody Map<String, Object> body, Authentication auth) {
        String username = auth.getName();
        Usuario usuario = usuarioRepositorio.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Map<String, String> keys = (Map<String, String>) body.get("keys");
        String endpoint = (String) body.get("endpoint");

        subscriptionRepo.findByUsuarioId(usuario.getId()).stream()
                .filter(s -> s.getEndpoint().equals(endpoint))
                .findFirst()
                .ifPresent(s -> subscriptionRepo.delete(s));

        PushSubscription sub = new PushSubscription();
        sub.setEndpoint(endpoint);
        sub.setP256dh(keys.get("p256dh"));
        sub.setAuth(keys.get("auth"));
        sub.setUsuario(usuario);
        subscriptionRepo.save(sub);

        return ResponseEntity.ok(Map.of("mensaje", "Suscripción guardada"));
    }
}