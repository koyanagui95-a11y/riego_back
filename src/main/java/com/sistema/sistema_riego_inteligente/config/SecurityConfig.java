package com.sistema.sistema_riego_inteligente.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Públicos
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/config/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/bomba/manual").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/guardar/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/sensores/registrar/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/dispositivo/**").permitAll()

                // Solo ADMIN (jefe) puede crear personal
                .requestMatchers(HttpMethod.POST, "/api/admin/personal").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/admin/personal").hasRole("ADMIN")

                // ADMIN y PERSONAL pueden gestionar clientes
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "PERSONAL")
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/config/**").hasAnyRole("ADMIN", "PERSONAL")

                // Todo lo demás requiere autenticación
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}