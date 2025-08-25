package com.power.operador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
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

    // Bean de diagnóstico temporal: imprime la configuración efectiva del datasource al arrancar.
    // NO dejar en producción permanente (contiene hash de la password). Elimina cuando resuelvas el problema.
    @Bean
    CommandLineRunner dsEcho(org.springframework.core.env.Environment env) {
        return args -> {
            String url  = env.getProperty("spring.datasource.url");
            String user = env.getProperty("spring.datasource.username");
            String pass = env.getProperty("spring.datasource.password");
            System.out.println(">>> DS.URL=" + url);
            System.out.println(">>> DS.USER=" + user);
            System.out.println(">>> DS.PASS.len=" + (pass == null ? 0 : pass.length()));
            try {
                var md  = java.security.MessageDigest.getInstance("SHA-256");
                var hex = java.util.HexFormat.of().formatHex(md.digest(pass == null ? new byte[0] : pass.getBytes()));
                System.out.println(">>> DS.PASS.sha256=" + hex);
            } catch (Exception ignored) {}
        };
    }



}
