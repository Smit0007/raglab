package com.raglab.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Process CORS preflight and headers before security authorization filters
            .cors(Customizer.withDefaults())
            // 2. Disable CSRF for stateless REST APIs
            .csrf(AbstractHttpConfigurer::disable)
            // 3. Stateless session management
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 4. Authorize requests: permit OPTIONS preflight unconditionally
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/v1/**").permitAll() // Preserves current Phase 2-4 API access contract
                .anyRequest().authenticated()
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 1. Explicitly allow frontend origin without wildcard
        config.setAllowedOrigins(List.of("http://localhost:3000"));

        // 2. Allow standard REST methods including preflight OPTIONS
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 3. Allow required headers including X-Tenant-ID and Authorization
        config.setAllowedHeaders(List.of("Content-Type", "X-Tenant-ID", "Authorization"));

        // 4. Exposed headers for client consumption
        config.setExposedHeaders(List.of("X-Tenant-ID", "traceparent"));

        // 5. Allow credentials if needed by browser fetch
        config.setAllowCredentials(true);

        // 6. Preflight response cache duration (1 hour)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
