package com.sistema.sistema_riego_inteligente.config;

import com.sistema.sistema_riego_inteligente.Entidad.Usuario;
import com.sistema.sistema_riego_inteligente.Repositorios.UsuarioRepositorio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired private UsuarioRepositorio usuarioRepositorio;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin.riego}")
    private String adminUsername;

    @Value("${app.admin.password:Admin1234}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        Optional<Usuario> adminOpt = usuarioRepositorio.findByUsername(adminUsername);

        if (adminOpt.isPresent()) {
            Usuario admin = adminOpt.get();
            if (!"ADMIN".equals(admin.getRol())) {
                admin.setRol("ADMIN");
                usuarioRepositorio.save(admin);
            }
            return;
        }

        Usuario admin = new Usuario();
        admin.setUsername(adminUsername);
        admin.setEmail(adminUsername + "@gmail.com");
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRol("ADMIN");
        usuarioRepositorio.save(admin);
    }
}