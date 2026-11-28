package com.sistema.sistema_riego_inteligente.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@EnableScheduling
@EnableAsync
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .requestMatchers("/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/push/vapid-public-key").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/config/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/bomba/estado").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/bomba/manual").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/guardar/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sensores/registrar/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/dispositivo/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/wifi/*/confirmar").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/wifi/**").hasAnyRole("CLIENTE", "ADMIN", "PERSONAL")
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "PERSONAL")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/config/**").hasAnyRole("ADMIN", "PERSONAL")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}