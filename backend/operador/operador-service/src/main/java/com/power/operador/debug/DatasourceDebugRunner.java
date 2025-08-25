package com.power.operador.debug;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DatasourceDebugRunner {

    @Bean
    CommandLineRunner dsEcho(Environment env) {
        return args -> {
            String url  = env.getProperty("spring.datasource.url");
            String user = env.getProperty("spring.datasource.username");
            String pass = env.getProperty("spring.datasource.password");
            System.out.println(">>> DS.URL=" + url);
            System.out.println(">>> DS.USER=" + user);
            System.out.println(">>> DS.PASS.len=" + (pass == null ? 0 : pass.length()));
            try {
                var md  = java.security.MessageDigest.getInstance("SHA-256");
                var hex = java.util.HexFormat.of().formatHex(
                        md.digest(pass == null ? new byte[0] : pass.getBytes()));
                System.out.println(">>> DS.PASS.sha256=" + hex);
            } catch (Exception ignored) {}
        };
    }
}