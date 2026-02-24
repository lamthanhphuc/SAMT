package com.samt.projectconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProjectConfigServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectConfigServiceApplication.class, args);
    }
}
