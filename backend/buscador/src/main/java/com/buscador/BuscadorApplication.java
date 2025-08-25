package com.buscador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Value;

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

    @Bean
    @Qualifier("plainRestTemplate")
    RestTemplate plainRestTemplate(
            RestTemplateBuilder builder,
            @Value("${ELASTICSEARCH_USERNAME:}") String user,
            @Value("${ELASTICSEARCH_PASSWORD:}") String pass
    ) {
        if (user != null && !user.isBlank()) {
            return builder.basicAuthentication(user, pass).build();
        }
        return builder.build();
    }

}


