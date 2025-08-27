package com.buscador.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean(name = "operadorRest")
    public RestTemplate operadorRest() {
        return new RestTemplate();
    }

    @Bean(name = "plainRestTemplate")
    public RestTemplate elasticRest() {
        return new RestTemplate();
    }
}
