package com.example.user_groupservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class UserGroupServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserGroupServiceApplication.class, args);
    }

}
