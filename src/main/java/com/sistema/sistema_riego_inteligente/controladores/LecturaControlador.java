package com.sistema.sistema_riego_inteligente.controladores;

import com.sistema.sistema_riego_inteligente.Entidad.Cliente;
import com.sistema.sistema_riego_inteligente.Entidad.Lectura;
import com.sistema.sistema_riego_inteligente.Entidad.Usuario;
import com.sistema.sistema_riego_inteligente.Repositorios.ClienteRepository;
import com.sistema.sistema_riego_inteligente.Repositorios.UsuarioRepositorio;
import com.sistema.sistema_riego_inteligente.servicios.ILecturaServicio;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class LecturaControlador {

    @Autowired private ILecturaServicio lecturaServicio;
    @Autowired private UsuarioRepositorio usuarioRepositorio;
    @Autowired private ClienteRepository clienteRepositorio;

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final long UMBRAL_ONLINE_MS = 20000L;

    // ──────────────────────────────────────────────────────────────────────
    @GetMapping("/dispositivo/{dispositivoId}/usuario")
    public ResponseEntity<?> resolverDispositivo(@PathVariable String dispositivoId) {
        Optional<Cliente> clienteOpt = clienteRepositorio.findByDispositivoId(dispositivoId);
        if (clienteOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("mensaje", "Dispositivo no registrado"));
        Cliente c = clienteOpt.get();
        Usuario u = c.getUsuario();
        Map<String, Object> resp = new HashMap<>();
        resp.put("usuarioId", u.getId());
        resp.put("username", u.getUsername());
        resp.put("estado", c.getEstado());
        resp.put("nombres", c.getNombres());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/lecturas/usuario/{usuarioId}")
    public ResponseEntity<Page<Lectura>> obtenerPorUsuario(@PathVariable Long usuarioId, Pageable pageable) {
        return ResponseEntity.ok(lecturaServicio.obtenerPorUsuarioId(usuarioId, pageable));
    }

    @GetMapping("/lecturas/estado/{usuarioId}")
    public ResponseEntity<?> obtenerUltimoEstadoPorUsuario(@PathVariable Long usuarioId) {
        Lectura lectura = lecturaServicio.obtenerUltimoEstadoPorUsuario(usuarioId);
        if (lectura == null) {
            return ResponseEntity.ok(Map.of(
                    "humedad", 0, "bombaEstado", false,
                    "mensaje", "Esperando primera lectura del dispositivo..."
            ));
        }
        return ResponseEntity.ok(lectura);
    }

    @PostMapping("/guardar/{usuarioId}")
    public ResponseEntity<?> guardarLectura(@PathVariable Long usuarioId, @Valid @RequestBody Lectura lectura) {
        Optional<Usuario> usuarioOpt = usuarioRepositorio.findById(usuarioId);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensaje", "Usuario no encontrado."));
        }
        lectura.setId(null);
        if (lectura.getFechaRegistro() == null) lectura.setFechaRegistro(LocalDateTime.now(ZONA_LIMA));
        lectura.setUsuario(usuarioOpt.get());
        return new ResponseEntity<>(lecturaServicio.guardar(lectura), HttpStatus.CREATED);
    }

    @DeleteMapping("/lecturas/{id}")
    public ResponseEntity<?> eliminarLectura(@PathVariable Long id) {
        lecturaServicio.eliminar(id);
        return ResponseEntity.ok(Map.of("eliminado", Boolean.TRUE));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  BOMBA MANUAL — ahora pasa usuarioId (fix multi-usuario)
    // ══════════════════════════════════════════════════════════════════════
    @PostMapping("/bomba/manual/encender")
    public ResponseEntity<?> activarManual(
            @RequestParam(value = "usuarioId", required = false, defaultValue = "1") Long usuarioId) {
        lecturaServicio.activarManual(usuarioId);
        return ResponseEntity.ok(Map.of("bombaManual", true, "mensaje", "Bomba activada manualmente"));
    }

    @PostMapping("/bomba/manual/apagar")
    public ResponseEntity<?> desactivarManual(
            @RequestParam(value = "usuarioId", required = false, defaultValue = "1") Long usuarioId) {
        lecturaServicio.desactivarManual(usuarioId);
        return ResponseEntity.ok(Map.of("bombaManual", false, "mensaje", "Bomba apagada manualmente"));
    }

    @GetMapping("/bomba/manual")
    public ResponseEntity<?> obtenerEstadoManual(
            @RequestParam(value = "usuarioId", required = false, defaultValue = "1") Long usuarioId) {
        lecturaServicio.registrarContactoEsp32(usuarioId);
        Map<String, Object> resp = new HashMap<>();
        resp.put("bombaManual", lecturaServicio.estaActivaManual(usuarioId));

        Map<String, String> wifi = lecturaServicio.consumirCambioWifi(usuarioId);
        if (wifi != null) {
            resp.put("wifiPendiente", true);
            resp.put("wifiSsid", wifi.get("ssid"));
            resp.put("wifiPassword", wifi.get("password"));
        } else {
            resp.put("wifiPendiente", false);
        }
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/bomba/estado")
    public ResponseEntity<?> obtenerEstadoEsp32(
            @RequestParam(value = "usuarioId", required = false, defaultValue = "1") Long usuarioId) {

        long ultimo = lecturaServicio.obtenerUltimoContactoPorUsuario(usuarioId);
        long ahora = System.currentTimeMillis();
        boolean online = ultimo > 0 && (ahora - ultimo) < UMBRAL_ONLINE_MS;

        Object tanqueEstado = null;
        Lectura ultimaLectura = lecturaServicio.obtenerUltimoEstadoPorUsuario(usuarioId);
        if (ultimaLectura != null && ultimaLectura.getExtras() != null) {
            tanqueEstado = ultimaLectura.getExtras().get("tanque");
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("bombaManual", lecturaServicio.estaActivaManual(usuarioId));
        resp.put("online", online);
        resp.put("ultimoContacto", ultimo);
        resp.put("tanqueEstado", tanqueEstado);
        resp.put("alertaFlujo", lecturaServicio.hayAlertaFlujo(usuarioId));   // NEW
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/wifi/{usuarioId}")
    public ResponseEntity<?> solicitarCambioWifi(@PathVariable Long usuarioId, @RequestBody Map<String, String> body) {
        String ssid = body.get("ssid");
        String password = body.get("password");
        if (ssid == null || ssid.trim().isEmpty() || password == null || password.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of(
                    "mensaje", "Ingresa un nombre de red válido y una contraseña de al menos 8 caracteres."
            ));
        }
        lecturaServicio.solicitarCambioWifi(usuarioId, ssid.trim(), password);
        return ResponseEntity.ok(Map.of("mensaje", "Solicitud enviada. El ESP32 cambiará de red en unos segundos."));
    }

    @PostMapping("/wifi/{usuarioId}/confirmar")
    public ResponseEntity<?> confirmarCambioWifi(@PathVariable Long usuarioId) {
        lecturaServicio.confirmarCambioWifi(usuarioId);
        return ResponseEntity.ok(Map.of("confirmado", true));
    }
}