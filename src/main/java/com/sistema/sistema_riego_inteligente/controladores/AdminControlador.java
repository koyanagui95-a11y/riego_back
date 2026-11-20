package com.sistema.sistema_riego_inteligente.controladores;

import com.sistema.sistema_riego_inteligente.Entidad.Cliente;
import com.sistema.sistema_riego_inteligente.Entidad.ConfiguracionSensor;
import com.sistema.sistema_riego_inteligente.Entidad.Usuario;
import com.sistema.sistema_riego_inteligente.Repositorios.ClienteRepository;
import com.sistema.sistema_riego_inteligente.Repositorios.ConfiguracionSensorRepository;
import com.sistema.sistema_riego_inteligente.Repositorios.LecturaRepository;
import com.sistema.sistema_riego_inteligente.Repositorios.PasswordResetTokenRepository;
import com.sistema.sistema_riego_inteligente.Repositorios.UsuarioRepositorio;
import com.sistema.sistema_riego_inteligente.dto.ActualizarClienteRequest;
import com.sistema.sistema_riego_inteligente.dto.RegistroClienteRequest;
import com.sistema.sistema_riego_inteligente.dto.RegisterRequest;
import com.sistema.sistema_riego_inteligente.dto.UserResponse;
import com.sistema.sistema_riego_inteligente.servicios.EmailService;
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
@PreAuthorize("hasRole('ADMIN')")
public class AdminControlador {

    @Autowired private UsuarioRepositorio usuarioRepositorio;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ConfiguracionSensorRepository configRepo;
    @Autowired private ClienteRepository clienteRepositorio;
    @Autowired private LecturaRepository lecturaRepo;
    @Autowired private PasswordResetTokenRepository tokenRepo;
    @Autowired private EmailService emailService;

    private static final String USUARIO_ADMIN = "admin.riego";
    private static final String[] SENSORES_DEFAULT = {"suelo", "dht", "temperatura", "caudal", "tanque", "bomba"};

    @PostMapping("/usuarios")
    @Transactional
    public ResponseEntity<?> crearUsuario(@Valid @RequestBody RegisterRequest request) {
        if (usuarioRepositorio.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.badRequest().body(new UserResponse(null, "El usuario ya existe."));
        }
        if (usuarioRepositorio.findByEmail(request.email()).isPresent()) {
            return ResponseEntity.badRequest().body(new UserResponse(null, "El correo ya está registrado."));
        }

        Usuario u = new Usuario();
        u.setUsername(request.username());
        u.setEmail(request.email());
        u.setPassword(passwordEncoder.encode(request.password()));
        u.setRol(USUARIO_ADMIN.equals(request.username()) ? "ADMIN" : "CLIENTE");
        Usuario guardado = usuarioRepositorio.save(u);

        if (USUARIO_ADMIN.equals(request.username())) {
            for (String tipo : SENSORES_DEFAULT) {
                ConfiguracionSensor c = new ConfiguracionSensor();
                c.setUsuario(guardado);
                c.setTipoSensor(tipo);
                c.setHabilitado(!tipo.equals("tanque"));
                c.setLabel(labelPorTipo(tipo));
                c.setUnidad(unidadPorTipo(tipo));
                c.setColor(colorPorTipo(tipo));
                c.setCampo(campoPorTipo(tipo));
                c.setTipoValor(tipoValorPorTipo(tipo));
                c.setOrden(ordenPorTipo(tipo));
                configRepo.save(c);
            }
        }

        return ResponseEntity.ok(new UserResponse(guardado.getId(), guardado.getUsername()));
    }

    @PostMapping("/clientes")
    @Transactional
    public ResponseEntity<?> crearCliente(@Valid @RequestBody RegistroClienteRequest request) {
        String username = generarUsername(request.nombres());
        String email = username + "@gmail.com";

        if (usuarioRepositorio.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "mensaje", "Ya existe un cliente con el nombre '" + username + "'. Usa otro nombre."));
        }

        String passwordRandom = UUID.randomUUID().toString().substring(0, 12);

        Usuario u = new Usuario();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(passwordRandom));
        u.setRol("CLIENTE");
        Usuario usuarioGuardado = usuarioRepositorio.save(u);

        Cliente c = new Cliente();
        c.setUsuario(usuarioGuardado);
        c.setNombres(request.nombres());
        c.setApellidos(request.apellidos());
        c.setCorreoPersonal(request.correoPersonal());
        c.setTelefono(request.telefono());
        c.setEstado("PENDIENTE");
        clienteRepositorio.save(c);

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", usuarioGuardado.getId());
        resp.put("clienteId", c.getId());
        resp.put("username", username);
        resp.put("email", email);
        resp.put("nombres", c.getNombres());
        resp.put("apellidos", c.getApellidos());
        resp.put("estado", c.getEstado());
        resp.put("mensaje", "Cliente creado en estado PENDIENTE.");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/clientes/{clienteId}/activar")
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
        c.setFechaActivacion(LocalDateTime.now(ZoneId.of("America/Lima")));
        clienteRepositorio.save(c);

        String destinatario = (c.getCorreoPersonal() != null && !c.getCorreoPersonal().isEmpty())
                ? c.getCorreoPersonal() : u.getEmail();

        emailService.enviarCredenciales(destinatario, c.getNombres(), u.getUsername(), passwordTemporal);

        return ResponseEntity.ok(Map.of(
                "clienteId", c.getId(),
                "username", u.getUsername(),
                "emailEnviado", destinatario,
                "estado", c.getEstado(),
                "mensaje", "Cliente activado. Credenciales enviadas a " + destinatario
        ));
    }

    @GetMapping("/clientes")
    public ResponseEntity<?> listarClientes() {
        List<Cliente> clientes = clienteRepositorio.findAllByOrderByFechaRegistroDesc();
        return ResponseEntity.ok(clientes);
    }

    @PutMapping("/clientes/{clienteId}")
    @Transactional
    public ResponseEntity<?> editarCliente(
            @PathVariable Long clienteId,
            @Valid @RequestBody ActualizarClienteRequest request) {

        Optional<Cliente> opt = clienteRepositorio.findById(clienteId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("mensaje", "Cliente no encontrado"));
        }

        Cliente c = opt.get();
        c.setNombres(request.nombres());
        c.setApellidos(request.apellidos());
        c.setCorreoPersonal(request.correoPersonal());
        c.setTelefono(request.telefono());
        clienteRepositorio.save(c);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Cliente actualizado correctamente",
                "clienteId", c.getId()
        ));
    }

    @DeleteMapping("/clientes/{clienteId}")
    @Transactional
    public ResponseEntity<?> eliminarCliente(@PathVariable Long clienteId) {
        Optional<Cliente> opt = clienteRepositorio.findById(clienteId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("mensaje", "Cliente no encontrado"));
        }

        Cliente c = opt.get();
        Usuario u = c.getUsuario();
        Long usuarioId = u.getId();

        tokenRepo.deleteByUsuarioId(usuarioId);
        lecturaRepo.deleteByUsuarioId(usuarioId);
        configRepo.deleteByUsuarioId(usuarioId);
        clienteRepositorio.delete(c);
        usuarioRepositorio.delete(u);

        return ResponseEntity.ok(Map.of("mensaje", "Cliente eliminado"));
    }

    @PostMapping("/clientes/{clienteId}/suspender")
    @Transactional
    public ResponseEntity<?> suspenderCliente(@PathVariable Long clienteId) {
        Optional<Cliente> opt = clienteRepositorio.findById(clienteId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("mensaje", "Cliente no encontrado"));
        }

        Cliente c = opt.get();
        c.setEstado("SUSPENDIDO");
        clienteRepositorio.save(c);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Servicio suspendido",
                "estado", c.getEstado()
        ));
    }

    @PostMapping("/clientes/{clienteId}/reactivar")
    @Transactional
    public ResponseEntity<?> reactivarCliente(@PathVariable Long clienteId) {
        Optional<Cliente> opt = clienteRepositorio.findById(clienteId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("mensaje", "Cliente no encontrado"));
        }

        Cliente c = opt.get();
        c.setEstado("ACTIVO");
        clienteRepositorio.save(c);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Servicio reactivado",
                "estado", c.getEstado()
        ));
    }

    @PostMapping("/personal")
    @Transactional
    public ResponseEntity<?> crearPersonal(@Valid @RequestBody RegisterRequest request) {
        if (usuarioRepositorio.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Ese usuario ya existe"));
        }
        if (usuarioRepositorio.findByEmail(request.email()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Ese correo ya está registrado"));
        }

        Usuario u = new Usuario();
        u.setUsername(request.username());
        u.setEmail(request.email());
        u.setPassword(passwordEncoder.encode(request.password()));
        u.setRol("ADMIN");
        usuarioRepositorio.save(u);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Personal admin creado",
                "username", u.getUsername(),
                "email", u.getEmail()
        ));
    }

    @GetMapping("/personal")
    public ResponseEntity<?> listarPersonal() {
        return ResponseEntity.ok(usuarioRepositorio.findAll().stream()
                .filter(u -> "ADMIN".equals(u.getRol()))
                .map(u -> Map.of("id", u.getId(), "username", u.getUsername(), "email", u.getEmail()))
                .toList());
    }

    private String generarUsername(String nombres) {
        if (nombres == null || nombres.trim().isEmpty()) return "cliente.riego";
        String primerNombre = nombres.trim().split("\\s+")[0].toLowerCase().replaceAll("[^a-z0-9]", "");
        return primerNombre.isEmpty() ? "cliente.riego" : primerNombre + ".riego";
    }

    private String labelPorTipo(String tipo) {
        switch (tipo) {
            case "suelo": return "Humedad del Suelo";
            case "dht": return "Humedad Ambiente";
            case "temperatura": return "Temperatura";
            case "caudal": return "Flujo de Agua";
            case "tanque": return "Nivel del Tanque";
            case "bomba": return "Bomba";
            default: return tipo;
        }
    }

    private String unidadPorTipo(String tipo) {
        switch (tipo) {
            case "suelo": case "dht": return "%";
            case "temperatura": return "°C";
            case "caudal": return "L/min";
            default: return "";
        }
    }

    private String colorPorTipo(String tipo) {
        switch (tipo) {
            case "suelo": return "success";
            case "dht": return "info";
            case "temperatura": return "danger";
            case "caudal": return "primary";
            case "tanque": return "primary";
            default: return "secondary";
        }
    }

    private String campoPorTipo(String tipo) {
        switch (tipo) {
            case "suelo": return "humedad_suelo";
            case "dht": return "humedad_ambiente";
            case "temperatura": return "temperatura";
            case "caudal": return "flujoAgua";
            case "tanque": return "extras.tanque";
            case "bomba": return "bombaActiva";
            default: return tipo;
        }
    }

    private String tipoValorPorTipo(String tipo) {
        return ("tanque".equals(tipo) || "bomba".equals(tipo)) ? "booleano" : "numero";
    }

    private int ordenPorTipo(String tipo) {
        switch (tipo) {
            case "suelo": return 1;
            case "dht": return 2;
            case "temperatura": return 3;
            case "caudal": return 4;
            case "tanque": return 5;
            case "bomba": return 99;
            default: return 50;
        }
    }
}