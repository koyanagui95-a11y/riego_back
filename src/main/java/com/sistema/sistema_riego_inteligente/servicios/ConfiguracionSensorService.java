package com.sistema.sistema_riego_inteligente.servicios;

import com.sistema.sistema_riego_inteligente.Entidad.ConfiguracionSensor;
import com.sistema.sistema_riego_inteligente.Entidad.Usuario;
import com.sistema.sistema_riego_inteligente.Repositorios.ConfiguracionSensorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Única fuente de verdad de los sensores por defecto. Antes estaba duplicada en
 * ConfiguracionInitializer y en AdminControlador, y los clientes nuevos no
 * recibían sensores hasta el siguiente reinicio del servidor.
 */
@Service
public class ConfiguracionSensorService {

    private record Def(String tipo, String label, String unidad, String color, String campo,
            String tipoValor, Map<String, Object> valores, int orden, boolean habilitado) {}

    private static final List<Def> DEFAULTS = List.of(
            new Def("suelo", "Humedad del Suelo", "%", "success", "humedad_suelo", "numero", Map.of(), 1, true),
            new Def("dht", "Humedad Ambiente", "%", "info", "humedad_ambiente", "numero", Map.of(), 2, true),
            new Def("temperatura", "Temperatura", "°C", "danger", "temperatura", "numero", Map.of(), 3, true),
            new Def("caudal", "Flujo de Agua", "L/min", "primary", "flujoAgua", "numero", Map.of(), 4, true),
            new Def("tanque", "Nivel del Tanque", "", "primary", "extras.tanque", "booleano",
                    Map.of("1", "LLENO", "0", "BAJO"), 5, false),
            new Def("bomba", "Bomba", "", "secondary", "bombaActiva", "booleano", Map.of(), 99, true)
    );

    private final ConfiguracionSensorRepository configRepo;

    public ConfiguracionSensorService(ConfiguracionSensorRepository configRepo) {
        this.configRepo = configRepo;
    }

    @Transactional
    public int asegurarDefaults(Usuario usuario) {
        Set<String> existentes = configRepo.findByUsuarioId(usuario.getId()).stream()
                .map(ConfiguracionSensor::getTipoSensor)
                .collect(Collectors.toSet());

        int creadas = 0;
        for (Def d : DEFAULTS) {
            if (existentes.contains(d.tipo())) {
                continue;
            }

            ConfiguracionSensor c = new ConfiguracionSensor();
            c.setUsuario(usuario);
            c.setTipoSensor(d.tipo());
            c.setLabel(d.label());
            c.setUnidad(d.unidad());
            c.setColor(d.color());
            c.setCampo(d.campo());
            c.setTipoValor(d.tipoValor());
            c.setValores(new HashMap<>(d.valores()));
            c.setOrden(d.orden());
            c.setHabilitado(d.habilitado());
            configRepo.save(c);
            creadas++;
        }
        return creadas;
    }
}
