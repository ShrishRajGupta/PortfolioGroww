package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Repositories live under this package and are picked up by Spring Boot's JPA auto-configuration;
// an explicit @EnableJpaRepositories here would also force JPA into web-slice tests (@WebMvcTest).
@SpringBootApplication
@EnableScheduling
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
