package com.sistema.sistema_riego_inteligente.controladores;

import com.sistema.sistema_riego_inteligente.Entidad.Cliente;
import com.sistema.sistema_riego_inteligente.Entidad.ConfiguracionSensor;
import com.sistema.sistema_riego_inteligente.Entidad.Usuario;
import com.sistema.sistema_riego_inteligente.Repositorios.ClienteRepository;
import com.sistema.sistema_riego_inteligente.Repositorios.ConfiguracionSensorRepository;
import com.sistema.sistema_riego_inteligente.Repositorios.LecturaRepository;
import com.sistema.sistema_riego_inteligente.Repositorios.PasswordResetTokenRepository;
import com.sistema.sistema_riego_inteligente.Repositorios.PushSubscriptionRepository;
import com.sistema.sistema_riego_inteligente.Repositorios.UsuarioRepositorio;
import com.sistema.sistema_riego_inteligente.dto.ActualizarClienteRequest;
import com.sistema.sistema_riego_inteligente.dto.RegistroClienteRequest;
import com.sistema.sistema_riego_inteligente.dto.RegisterRequest;
import com.sistema.sistema_riego_inteligente.dto.UserResponse;
import com.sistema.sistema_riego_inteligente.servicios.EmailService;
import com.sistema.sistema_riego_inteligente.servicios.WebPushService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('ADMIN','PERSONAL')")
public class AdminControlador {

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ConfiguracionSensorRepository configRepo;
    @Autowired
    private ClienteRepository clienteRepositorio;
    @Autowired
    private LecturaRepository lecturaRepo;
    @Autowired
    private PasswordResetTokenRepository tokenRepo;
    @Autowired
    private PushSubscriptionRepository pushRepo;
    @Autowired
    private EmailService emailService;
    @Autowired
    private WebPushService webPushService;

    private static final String USUARIO_ADMIN = "admin.riego";
    private static final String[] SENSORES_DEFAULT = {"suelo", "dht", "temperatura", "caudal", "tanque", "bomba"};
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    @PostMapping("/clientes")
    @PreAuthorize("hasAnyRole('ADMIN','PERSONAL')")
    @Transactional
    public ResponseEntity<?> crearCliente(@Valid @RequestBody RegistroClienteRequest request) {
        String username = generarUsernameUnico(request.nombres(), request.apellidos());
        String emailSistema = username + "@gmail.com";
        String correoPersonal = request.correoPersonal() != null ? request.correoPersonal().trim().toLowerCase() : "";

        if (!correoPersonal.isEmpty()) {
            if (usuarioRepositorio.findByEmail(correoPersonal).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "mensaje", "Ese correo ya está registrado en el sistema."));
            }
            Optional<Cliente> existente = clienteRepositorio.findByCorreoPersonal(correoPersonal);
            if (existente.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "mensaje", "Este correo personal ya le pertenece al cliente: " + existente.get().getNombres() + " " + existente.get().getApellidos()));
            }
        }

        String passwordRandom = UUID.randomUUID().toString().substring(0, 12);

        Usuario u = new Usuario();
        u.setUsername(username);
        u.setEmail(emailSistema);
        u.setPassword(passwordEncoder.encode(passwordRandom));
        u.setRol("CLIENTE");
        Usuario usuarioGuardado = usuarioRepositorio.save(u);

        Cliente c = new Cliente();
        c.setUsuario(usuarioGuardado);
        c.setNombres(request.nombres());
        c.setApellidos(request.apellidos());
        c.setCorreoPersonal(correoPersonal);
        c.setTelefono(request.telefono());
        c.setEstado("PENDIENTE");
        c.setDescripcionServicio(request.descripcionServicio());
        if (request.costoSistema() != null) {
            c.setCostoSistema(request.costoSistema());
        }
        if (request.costoSensores() != null) {
            c.setCostoSensores(request.costoSensores());
        }
        if (request.costoComponentes() != null) {
            c.setCostoComponentes(request.costoComponentes());
        }
        if (request.costoMensual() != null) {
            c.setCostoMensual(request.costoMensual());
        }
        clienteRepositorio.save(c);

        inicializarSensoresPorDefecto(usuarioGuardado);

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", usuarioGuardado.getId());
        resp.put("clienteId", c.getId());
        resp.put("dispositivoId", c.getDispositivoId());
        resp.put("username", username);
        resp.put("email", emailSistema);
        resp.put("nombres", c.getNombres());
        resp.put("apellidos", c.getApellidos());
        resp.put("estado", c.getEstado());
        resp.put("mensaje", "Cliente creado en estado PENDIENTE.");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/clientes")
    public ResponseEntity<?> listarClientes() {
        List<Cliente> clientes = clienteRepositorio.findAllByOrderByFechaRegistroDesc();
        return ResponseEntity.ok(clientes);
    }

    @PostMapping("/usuarios")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> crearUsuario(@Valid @RequestBody RegisterRequest request) {
        String emailNormalizado = request.email() != null ? request.email().trim().toLowerCase() : "";

        if (usuarioRepositorio.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.badRequest().body(new UserResponse(null, "El usuario ya existe."));
        }
        if (usuarioRepositorio.findByEmail(emailNormalizado).isPresent()
                || clienteRepositorio.findByCorreoPersonal(emailNormalizado).isPresent()) {
            return ResponseEntity.badRequest().body(new UserResponse(null, "El correo ya está registrado."));
        }

        Usuario u = new Usuario();
        u.setUsername(request.username());
        u.setEmail(emailNormalizado);
        u.setPassword(passwordEncoder.encode(request.password()));
        u.setRol(USUARIO_ADMIN.equals(request.username()) ? "ADMIN" : "CLIENTE");
        Usuario guardado = usuarioRepositorio.save(u);

        if (USUARIO_ADMIN.equals(request.username())) {
            inicializarSensoresPorDefecto(guardado);
        }

        return ResponseEntity.ok(new UserResponse(guardado.getId(), guardado.getUsername()));
    }

    @PostMapping("/clientes/{clienteId}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> activarCliente(
            @PathVariable Long clienteId,
            @RequestParam(value = "password", required = false) String passwordOverride) {

        Optional<Cliente> clienteOpt = clienteRepositorio.findById(clienteId);
        if (clienteOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("mensaje", "Cliente no encontrado"));
        }

        Cliente c = clienteOpt.get();
        if ("ACTIVO".equals(c.getEstado())) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "El cliente ya está activo"));
        }

        String passwordTemporal = (passwordOverride != null && !passwordOverride.isEmpty())
                ? passwordOverride
                : UUID.randomUUID().toString().substring(0, 12);

        Usuario u = c.getUsuario();
        u.setPassword(passwordEncoder.encode(passwordTemporal));
        usuarioRepositorio.save(u);

        c.setEstado("ACTIVO");
        c.setFechaActivacion(LocalDateTime.now(ZONA_LIMA));
        if (c.getFechaProximoPago() == null) {
            c.setFechaProximoPago(LocalDateTime.now(ZONA_LIMA).plusDays(30));
        }
        clienteRepositorio.save(c);

        String destinatario = (c.getCorreoPersonal() != null && !c.getCorreoPersonal().isEmpty())
                ? c.getCorreoPersonal() : u.getEmail();

        try {
            emailService.enviarCredenciales(destinatario, c.getNombres(), u.getUsername(), passwordTemporal);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "mensaje", "Cliente activado, pero no se pudo enviar el correo: " + e.getMessage(),
                    "clienteId", c.getId(),
                    "username", u.getUsername(),
                    "emailFallido", destinatario
            ));
        }

        return ResponseEntity.ok(Map.of(
                "clienteId", c.getId(),
                "username", u.getUsername(),
                "emailEnviado", destinatario,
                "estado", c.getEstado(),
                "mensaje", "Cliente activado. Credenciales enviadas a " + destinatario
        ));
    }

    @PutMapping("/clientes/{clienteId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> editarCliente(
            @PathVariable Long clienteId,
            @Valid @RequestBody ActualizarClienteRequest request) {

        Optional<Cliente> opt = clienteRepositorio.findById(clienteId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("mensaje", "Cliente no encontrado"));
        }

        String nuevoCorreo = request.correoPersonal() != null ? request.correoPersonal().trim().toLowerCase() : "";
        if (!nuevoCorreo.isEmpty()) {
            Optional<Cliente> duplicadoCliente = clienteRepositorio.findByCorreoPersonal(nuevoCorreo);
            if (duplicadoCliente.isPresent() && !duplicadoCliente.get().getId().equals(clienteId)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "mensaje", "Este correo personal ya le pertenece a otro cliente."));
            }
        }

        Cliente c = opt.get();
        c.setNombres(request.nombres());
        c.setApellidos(request.apellidos());
        c.setCorreoPersonal(nuevoCorreo);
        c.setTelefono(request.telefono());
        clienteRepositorio.save(c);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Cliente actualizado correctamente",
                "clienteId", c.getId()
        ));
    }

    @DeleteMapping("/clientes/{clienteId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> eliminarCliente(@PathVariable Long clienteId) {
        Optional<Cliente> opt = clienteRepositorio.findById(clienteId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("mensaje", "Cliente no encontrado"));
        }

        Cliente c = opt.get();
        Usuario u = c.getUsuario();
        Long usuarioId = u.getId();

        pushRepo.deleteByUsuarioId(usuarioId);
        tokenRepo.deleteByUsuarioId(usuarioId);
        lecturaRepo.deleteByUsuarioId(usuarioId);
        configRepo.deleteByUsuarioId(usuarioId);
        clienteRepositorio.delete(c);
        usuarioRepositorio.delete(u);

        return ResponseEntity.ok(Map.of("mensaje", "Cliente eliminado"));
    }

    @PostMapping("/clientes/{clienteId}/suspender")
    @PreAuthorize("hasAnyRole('ADMIN','PERSONAL')")
    @Transactional
    public ResponseEntity<?> suspenderCliente(
            @PathVariable Long clienteId,
            @RequestBody(required = false) Map<String, String> body) {

        Optional<Cliente> opt = clienteRepositorio.findById(clienteId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("mensaje", "Cliente no encontrado"));
        }

        String motivo = (body != null && body.get("motivo") != null && !body.get("motivo").isBlank())
                ? body.get("motivo").trim()
                : "Falta de pago o revisión administrativa.";

        Cliente c = opt.get();
        c.setEstado("SUSPENDIDO");
        clienteRepositorio.save(c);

        Long usuarioId = c.getUsuario().getId();
        webPushService.enviarNotificacionAsync(
                usuarioId,
                "Servicio suspendido",
                c.getNombres() + ", tu servicio fue suspendido. Motivo: " + motivo,
                "servicio-suspendido"
        );

        String destinatario = (c.getCorreoPersonal() != null && !c.getCorreoPersonal().isEmpty())
                ? c.getCorreoPersonal() : c.getUsuario().getEmail();
        emailService.enviarNotificacionEstadoServicio(destinatario, c.getNombres(), true, motivo);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Servicio suspendido",
                "motivo", motivo,
                "estado", c.getEstado()
        ));
    }

    @PostMapping("/clientes/{clienteId}/reactivar")
    @PreAuthorize("hasAnyRole('ADMIN','PERSONAL')")
    @Transactional
    public ResponseEntity<?> reactivarCliente(@PathVariable Long clienteId) {
        Optional<Cliente> opt = clienteRepositorio.findById(clienteId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("mensaje", "Cliente no encontrado"));
        }

        Cliente c = opt.get();
        c.setEstado("ACTIVO");
        clienteRepositorio.save(c);

        Long usuarioId = c.getUsuario().getId();
        webPushService.enviarNotificacionAsync(
                usuarioId,
                "Servicio reactivado",
                c.getNombres() + ", tu servicio de riego ha sido reactivado.",
                "servicio-reactivado"
        );

        String destinatario = (c.getCorreoPersonal() != null && !c.getCorreoPersonal().isEmpty())
                ? c.getCorreoPersonal() : c.getUsuario().getEmail();
        emailService.enviarNotificacionEstadoServicio(destinatario, c.getNombres(), false, null);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Servicio reactivado",
                "estado", c.getEstado()
        ));
    }

    @PostMapping("/personal")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> crearPersonal(@Valid @RequestBody RegisterRequest request) {
        String emailNormalizado = request.email() != null ? request.email().trim().toLowerCase() : "";

        if (usuarioRepositorio.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Ese usuario ya existe"));
        }
        if (usuarioRepositorio.findByEmail(emailNormalizado).isPresent()
                || clienteRepositorio.findByCorreoPersonal(emailNormalizado).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Ese correo ya está registrado"));
        }

        Usuario u = new Usuario();
        u.setUsername(request.username());
        u.setEmail(emailNormalizado);
        u.setPassword(passwordEncoder.encode(request.password()));
        u.setRol("PERSONAL");
        usuarioRepositorio.save(u);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Personal administrativo creado",
                "username", u.getUsername(),
                "email", u.getEmail(),
                "rol", u.getRol()
        ));
    }

    @GetMapping("/personal")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> listarPersonal() {
        return ResponseEntity.ok(usuarioRepositorio.findAll().stream()
                .filter(u -> "ADMIN".equals(u.getRol()) || "PERSONAL".equals(u.getRol()))
                .map(u -> Map.of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "email", u.getEmail(),
                        "rol", u.getRol()
                ))
                .toList());
    }

    @PutMapping("/clientes/{clienteId}/precios")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> actualizarPrecios(
            @PathVariable Long clienteId,
            @RequestBody Map<String, Object> precios) {

        Optional<Cliente> opt = clienteRepositorio.findById(clienteId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("mensaje", "Cliente no encontrado"));
        }

        Cliente c = opt.get();
        if (precios.containsKey("costoSistema") && precios.get("costoSistema") instanceof Number n) {
            c.setCostoSistema(n.doubleValue());
        }
        if (precios.containsKey("costoSensores") && precios.get("costoSensores") instanceof Number n) {
            c.setCostoSensores(n.doubleValue());
        }
        if (precios.containsKey("costoComponentes") && precios.get("costoComponentes") instanceof Number n) {
            c.setCostoComponentes(n.doubleValue());
        }
        if (precios.containsKey("costoMensual") && precios.get("costoMensual") instanceof Number n) {
            c.setCostoMensual(n.doubleValue());
        }
        if (precios.containsKey("diasGracia") && precios.get("diasGracia") instanceof Number n) {
            c.setDiasGracia(n.intValue());
        }
        if (precios.containsKey("descripcionServicio") && precios.get("descripcionServicio") instanceof String s) {
            c.setDescripcionServicio(s);
        }

        clienteRepositorio.save(c);

        return ResponseEntity.ok(Map.of("mensaje", "Precios actualizados"));
    }

    @PostMapping("/clientes/{clienteId}/pago")
    @PreAuthorize("hasAnyRole('ADMIN','PERSONAL')")
    @Transactional
    public ResponseEntity<?> registrarPago(
            @PathVariable Long clienteId,
            @RequestParam(value = "meses", required = false, defaultValue = "1") int meses) {

        Optional<Cliente> opt = clienteRepositorio.findById(clienteId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("mensaje", "Cliente no encontrado"));
        }

        Cliente c = opt.get();
        LocalDateTime ahoraLima = LocalDateTime.now(ZONA_LIMA);
        LocalDateTime base = (c.getFechaProximoPago() != null && c.getFechaProximoPago().isAfter(ahoraLima))
                ? c.getFechaProximoPago()
                : ahoraLima;

        c.setFechaProximoPago(base.plusMonths(meses));
        boolean reactivado = false;
        if ("SUSPENDIDO".equals(c.getEstado())) {
            c.setEstado("ACTIVO");
            reactivado = true;
        }
        clienteRepositorio.save(c);

        if (reactivado) {
            Long usuarioId = c.getUsuario().getId();
            webPushService.enviarNotificacionAsync(
                    usuarioId,
                    "Servicio reactivado",
                    c.getNombres() + ", tu pago fue registrado y tu servicio ha sido reactivado.",
                    "servicio-reactivado"
            );
            String destinatario = (c.getCorreoPersonal() != null && !c.getCorreoPersonal().isEmpty())
                    ? c.getCorreoPersonal() : c.getUsuario().getEmail();
            emailService.enviarNotificacionEstadoServicio(destinatario, c.getNombres(), false, null);
        }

        return ResponseEntity.ok(Map.of(
                "mensaje", "Pago registrado. Próximo vencimiento: " + c.getFechaProximoPago(),
                "fechaProximoPago", c.getFechaProximoPago(),
                "estado", c.getEstado()
        ));
    }

    private void inicializarSensoresPorDefecto(Usuario u) {
        for (String tipo : SENSORES_DEFAULT) {
            if (configRepo.findByUsuarioIdAndTipoSensor(u.getId(), tipo).isPresent()) {
                continue;
            }
            ConfiguracionSensor c = new ConfiguracionSensor();
            c.setUsuario(u);
            c.setTipoSensor(tipo);
            c.setHabilitado(!tipo.equals("tanque"));
            c.setLabel(labelPorTipo(tipo));
            c.setUnidad(unidadPorTipo(tipo));
            c.setColor(colorPorTipo(tipo));
            c.setCampo(campoPorTipo(tipo));
            c.setTipoValor(tipoValorPorTipo(tipo));
            c.setOrden(ordenPorTipo(tipo));
            if ("tanque".equals(tipo)) {
                c.setValores(Map.of("1", "LLENO", "0", "BAJO"));
            }
            configRepo.save(c);
        }
    }

    private String generarUsernameUnico(String nombres, String apellidos) {
        String base = "cliente";
        if (nombres != null && !nombres.trim().isEmpty()) {
            String primerNombre = nombres.trim().split("\\s+")[0].toLowerCase().replaceAll("[^a-z0-9]", "");
            if (!primerNombre.isEmpty()) {
                base = primerNombre;
            }
        }
        String candidato = base + ".riego";
        if (usuarioRepositorio.findByUsername(candidato).isEmpty()) {
            return candidato;
        }
        if (apellidos != null && !apellidos.trim().isEmpty()) {
            String primerApellido = apellidos.trim().split("\\s+")[0].toLowerCase().replaceAll("[^a-z0-9]", "");
            if (!primerApellido.isEmpty()) {
                candidato = base + "." + primerApellido + ".riego";
                if (usuarioRepositorio.findByUsername(candidato).isEmpty()) {
                    return candidato;
                }
            }
        }
        int contador = 2;
        while (true) {
            candidato = base + contador + ".riego";
            if (usuarioRepositorio.findByUsername(candidato).isEmpty()) {
                return candidato;
            }
            contador++;
        }
    }

    private String labelPorTipo(String tipo) {
        return switch (tipo) {
            case "suelo" ->
                "Humedad del Suelo";
            case "dht" ->
                "Humedad Ambiente";
            case "temperatura" ->
                "Temperatura";
            case "caudal" ->
                "Flujo de Agua";
            case "tanque" ->
                "Nivel del Tanque";
            case "bomba" ->
                "Bomba";
            default ->
                tipo;
        };
    }

    private String unidadPorTipo(String tipo) {
        return switch (tipo) {
            case "suelo", "dht" ->
                "%";
            case "temperatura" ->
                "°C";
            case "caudal" ->
                "L/min";
            default ->
                "";
        };
    }

    private String colorPorTipo(String tipo) {
        return switch (tipo) {
            case "suelo" ->
                "success";
            case "dht" ->
                "info";
            case "temperatura" ->
                "danger";
            case "caudal", "tanque" ->
                "primary";
            default ->
                "secondary";
        };
    }

    private String campoPorTipo(String tipo) {
        return switch (tipo) {
            case "suelo" ->
                "humedad_suelo";
            case "dht" ->
                "humedad_ambiente";
            case "temperatura" ->
                "temperatura";
            case "caudal" ->
                "flujoAgua";
            case "tanque" ->
                "extras.tanque";
            case "bomba" ->
                "bombaActiva";
            default ->
                tipo;
        };
    }

    private String tipoValorPorTipo(String tipo) {
        return ("tanque".equals(tipo) || "bomba".equals(tipo)) ? "booleano" : "numero";
    }

    private int ordenPorTipo(String tipo) {
        return switch (tipo) {
            case "suelo" ->
                1;
            case "dht" ->
                2;
            case "temperatura" ->
                3;
            case "caudal" ->
                4;
            case "tanque" ->
                5;
            case "bomba" ->
                99;
            default ->
                50;
        };
    }
}
