package com.opensms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OpenSmsApplication {

    public static void main(String[] args) {

        System.out.println("DB_USERNAME loaded: " +
                (System.getenv("DB_USERNAME")));


        SpringApplication.run(OpenSmsApplication.class, args);
    }
}
