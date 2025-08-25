package com.buscador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling  // ✅ importante hacer que spring boot reconozca las tareas programadas
public class BuscadorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuscadorApplication.class, args);
    }

    @Bean
    @LoadBalanced
    @Qualifier("eurekaRestTemplate")
    public RestTemplate eurekaRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    @Qualifier("plainRestTemplate")
    public RestTemplate plainRestTemplate() {
        return new RestTemplate();
    }

}


