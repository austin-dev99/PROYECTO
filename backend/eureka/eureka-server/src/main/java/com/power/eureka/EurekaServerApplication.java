package com.power.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
// EnableEurekaServer annotation is used to enable the Eureka server functionality
// This application will act as a Eureka server for service discovery
public class EurekaServerApplication {

	public static void main(String[] args) {



        SpringApplication.run(EurekaServerApplication.class, args);
	}

}
