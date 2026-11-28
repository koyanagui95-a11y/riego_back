package com.sistema.sistema_riego_inteligente.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Garantiza que @Scheduled (Esp32Monitor) esté activo aunque la clase main
 * no tenga @EnableScheduling. Declararlo dos veces no causa problemas.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
