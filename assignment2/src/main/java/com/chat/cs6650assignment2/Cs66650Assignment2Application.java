package com.chat.cs6650assignment2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class Cs66650Assignment2Application {

	public static void main(String[] args) {
		SpringApplication.run(Cs66650Assignment2Application.class, args);
	}

}
