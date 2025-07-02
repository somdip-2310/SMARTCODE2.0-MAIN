package com.somdiproy.smartcodereview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SmartCodeReviewApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartCodeReviewApplication.class, args);
    }
}