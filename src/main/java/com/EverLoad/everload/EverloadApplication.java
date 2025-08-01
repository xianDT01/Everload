package com.EverLoad.everload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EverloadApplication {

	public static void main(String[] args) {
		SpringApplication.run(EverloadApplication.class, args);
	}

}
