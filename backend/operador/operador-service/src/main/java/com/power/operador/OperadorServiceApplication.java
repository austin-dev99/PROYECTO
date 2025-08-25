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
    @Bean
    CommandLineRunner checkEnv(org.springframework.core.env.Environment env) {
        return args -> {
            String url = env.getProperty("spring.datasource.url");
            String user = env.getProperty("spring.datasource.username");
            String pass = env.getProperty("spring.datasource.password");
            System.out.println(">>> spring.datasource.url=" + url);
            System.out.println(">>> spring.datasource.username=" + user);
            System.out.println(">>> spring.datasource.password.length=" + (pass == null ? 0 : pass.length()));
        };
    }

}
