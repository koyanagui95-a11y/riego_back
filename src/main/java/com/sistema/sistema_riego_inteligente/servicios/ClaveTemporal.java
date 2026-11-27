package com.sistema.sistema_riego_inteligente.servicios;

import java.security.SecureRandom;

/** Generador de contraseñas temporales con SecureRandom (sin caracteres ambiguos como 0/O, 1/l/I). */
public final class ClaveTemporal {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ClaveTemporal() {}

    public static String generar(int largo) {
        StringBuilder sb = new StringBuilder(largo);
        for (int i = 0; i < largo; i++) {
            sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
