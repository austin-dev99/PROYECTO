package com.power.operador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;


@SpringBootApplication
@EnableDiscoveryClient
public class OperadorServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OperadorServiceApplication.class, args);
	}


    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {   // <- usa Eureka
        return new RestTemplate();
    }




}
