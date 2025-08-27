package com.buscador.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean(name = "operadorRest")
    public RestTemplate operadorRest() {
        return createRestTemplate(5000, 5000); // 5s timeout
    }

    @Bean(name = "elasticRest")
    public RestTemplate elasticRest() {
        return createRestTemplate(5000, 5000); // 5s timeout
    }

    private RestTemplate createRestTemplate(int connectTimeout, int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
}
