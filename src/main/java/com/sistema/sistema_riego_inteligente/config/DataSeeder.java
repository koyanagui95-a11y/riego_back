package com.sistema.sistema_riego_inteligente.config;

import com.sistema.sistema_riego_inteligente.Entidad.Usuario;
import com.sistema.sistema_riego_inteligente.Repositorios.UsuarioRepositorio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired private UsuarioRepositorio usuarioRepositorio;
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        Optional<Usuario> adminOpt = usuarioRepositorio.findByUsername("admin.riego");

        if (adminOpt.isPresent()) {
            Usuario admin = adminOpt.get();
            if (!"ADMIN".equals(admin.getRol())) {
                admin.setRol("ADMIN");
                usuarioRepositorio.save(admin);
                System.out.println("[DataSeeder] Rol de admin.riego actualizado a ADMIN");
            } else {
                System.out.println("[DataSeeder] admin.riego ya tiene rol ADMIN");
            }
            return;
        }

        Usuario admin = new Usuario();
        admin.setUsername("admin.riego");
        admin.setEmail("admin.riego@gmail.com");
        admin.setPassword(passwordEncoder.encode("Admin1234"));
        admin.setRol("ADMIN");
        usuarioRepositorio.save(admin);
        System.out.println("[DataSeeder] admin.riego creado con rol ADMIN");
    }
}