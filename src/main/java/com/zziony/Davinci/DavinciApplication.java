package com.zziony.Davinci;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

@SpringBootApplication(exclude={SecurityAutoConfiguration.class})
public class DavinciApplication {

	public static void main(String[] args) {
		SpringApplication.run(DavinciApplication.class, args);

	}

}
