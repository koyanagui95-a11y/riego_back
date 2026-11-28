package com.sistema.sistema_riego_inteligente.config;

import java.security.Principal;

/**
 * Identidad del usuario autenticado vía JWT. Se guarda como "principal" en el
 * SecurityContext para poder verificar propiedad de recursos sin consultar la BD.
 */
public record UsuarioAutenticado(Long id, String username, String rol) implements Principal {

    @Override
    public String getName() {
        return username;
    }

    public boolean esStaff() {
        return "ADMIN".equals(rol) || "PERSONAL".equals(rol);
    }
}
