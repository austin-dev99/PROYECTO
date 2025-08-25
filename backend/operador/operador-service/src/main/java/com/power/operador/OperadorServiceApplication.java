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

    // Bean de diagnóstico temporal (controlado por propiedad debug.datasource.echo=true)
    @Bean
    CommandLineRunner dsEcho(org.springframework.core.env.Environment env) {
        return args -> {
            if (!Boolean.parseBoolean(env.getProperty("debug.datasource.echo", "false"))) return;
            String url  = env.getProperty("spring.datasource.url", "");
            String user = env.getProperty("spring.datasource.username", "");
            String pass = env.getProperty("spring.datasource.password");

            String safeUrl = url.replaceAll("(//)([^:@/]{1,20})(:.*?@)", "$1***:***@"); // oculta user:pass si viniera en la URL
            String userMask = user.isEmpty() ? "" : (user.length() <= 2 ? "**" : user.charAt(0) + "***" + user.charAt(user.length()-1));

            System.out.println("[DS] url=" + safeUrl);
            System.out.println("[DS] user=" + userMask);
            System.out.println("[DS] pass.len=" + (pass == null ? 0 : pass.length()));
            try {
                var md  = java.security.MessageDigest.getInstance("SHA-256");
                var hex = java.util.HexFormat.of().formatHex(md.digest(pass == null ? new byte[0] : pass.getBytes()));
                System.out.println("[DS] pass.sha256=" + hex.substring(0, 16) + "..." );
            } catch (Exception ignored) {}
        };
    }



}
