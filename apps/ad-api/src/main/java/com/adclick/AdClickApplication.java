package com.adclick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AdClickApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdClickApplication.class, args);
    }
}
