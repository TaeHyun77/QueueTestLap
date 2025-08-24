package com.example.QueueTestLab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class QueueTestLabApplication {

	public static void main(String[] args) {
		SpringApplication.run(QueueTestLabApplication.class, args);
	}

}
