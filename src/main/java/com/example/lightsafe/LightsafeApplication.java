package com.example.lightsafe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;


@SpringBootApplication
public class LightsafeApplication {

	public static void main(String[] args) {
		SpringApplication.run(LightsafeApplication.class, args);
	}

}