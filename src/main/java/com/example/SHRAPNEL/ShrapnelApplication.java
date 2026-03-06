package com.example.SHRAPNEL;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShrapnelApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShrapnelApplication.class, args);
	}

}
