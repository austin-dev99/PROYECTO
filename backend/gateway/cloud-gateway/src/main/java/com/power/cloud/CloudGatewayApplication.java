package com.power.cloud;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootApplication
@EnableDiscoveryClient
public class CloudGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudGatewayApplication.class, args);
    }

    /**
     * Configuración CORS simple sin wildcard.
     * Lee CORS_ALLOWED_ORIGINS (lista separada por comas) y la aplica como allowedOrigins.
     * Ejemplo de variable:
     *   CORS_ALLOWED_ORIGINS = http://localhost:3000, https://power-fit-react.vercel.app
     */
    @Bean
    public CorsWebFilter corsWebFilter(
            @Value("${CORS_ALLOWED_ORIGINS:https://power-fit-react.vercel.app") String originsProp
    ) {
        // Normaliza: separa por coma, quita espacios y slashes finales
        List<String> origins = Arrays.stream(originsProp.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.replaceAll("/+$", "")) // quita cualquier barra final
                .collect(Collectors.toList());

        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowCredentials(true);
        cors.setAllowedHeaders(List.of("*"));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedOrigins(origins);
        // (Opcional) Exponer cabeceras si necesitas leer alguna personalizada:
        // cors.setExposedHeaders(List.of("Location"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return new CorsWebFilter(source);
    }
}