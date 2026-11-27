package com.sistema.sistema_riego_inteligente.servicios;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador simple de intentos de login (en memoria) para frenar fuerza bruta.
 * 5 fallos dentro de 15 min -> bloqueo de 10 min para esa combinación usuario+IP.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_FALLOS = 5;
    private static final long VENTANA_MS = 15 * 60_000L;
    private static final long BLOQUEO_MS = 10 * 60_000L;
    private static final int LIMITE_MAPA = 5_000;

    private record Intento(int fallos, long ultimoFallo, long bloqueadoHasta) {}

    private final ConcurrentHashMap<String, Intento> intentos = new ConcurrentHashMap<>();

    public boolean estaBloqueado(String clave) {
        Intento i = intentos.get(clave);
        return i != null && i.bloqueadoHasta() > System.currentTimeMillis();
    }

    public void registrarFallo(String clave) {
        long ahora = System.currentTimeMillis();
        intentos.compute(clave, (k, previo) -> {
            int fallos = (previo == null || ahora - previo.ultimoFallo() > VENTANA_MS) ? 1 : previo.fallos() + 1;
            if (fallos >= MAX_FALLOS) {
                return new Intento(0, ahora, ahora + BLOQUEO_MS);
            }
            return new Intento(fallos, ahora, 0L);
        });
        if (intentos.size() > LIMITE_MAPA) {
            intentos.entrySet().removeIf(e ->
                    e.getValue().bloqueadoHasta() < ahora && ahora - e.getValue().ultimoFallo() > VENTANA_MS);
        }
    }

    public void registrarExito(String clave) {
        intentos.remove(clave);
    }
}
