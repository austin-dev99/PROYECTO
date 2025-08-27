package com.power.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Gateway principal.
 *
 * NOTA:
 * - La configuración CORS se hace vía application.yml (globalcors).
 * - No definimos aquí CorsWebFilter.
 * - @EnableDiscoveryClient no es necesaria si eureka.client.enabled=false.
 *   Si más adelante habilitas Eureka, puedes añadirla de nuevo.
 */
@SpringBootApplication
public class CloudGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudGatewayApplication.class, args);
    }
}