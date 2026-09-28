package com.clearledger.ledger.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    @Profile("!dev & !test")
    SecurityFilterChain productionSecurity(HttpSecurity http) throws Exception {
        return common(http)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/docs/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyAuthority("SCOPE_ledger:read", "SCOPE_ledger:write")
                .requestMatchers("/api/v1/**").hasAuthority("SCOPE_ledger:write")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
            .build();
    }

    @Bean
    @Profile({"dev", "test"})
    SecurityFilterChain developmentSecurity(HttpSecurity http) throws Exception {
        return common(http)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/docs/**", "/v3/api-docs/**", "/api/v1/**").permitAll()
                .anyRequest().authenticated())
            .build();
    }

    private HttpSecurity common(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(
                org.springframework.security.config.http.SessionCreationPolicy.STATELESS));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${clearledger.security.allowed-origins}") List<String> origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins); config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Tenant-Id", "Idempotency-Key", "X-Request-Id"));
        config.setExposedHeaders(List.of("Location", "X-Request-Id"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
