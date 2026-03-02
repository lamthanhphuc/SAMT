package com.example.gateway;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.springframework.web.server.handler.ExceptionHandlingWebHandler;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    @Bean
    public CommandLineRunner beanRegistryChecker(ApplicationContext applicationContext) {
        return args -> {
            String[] beanNames = applicationContext.getBeanNamesForType(ErrorWebExceptionHandler.class);
            log.info("🔍 ERROR HANDLER BEANS FOUND: {}", java.util.Arrays.toString(beanNames));
            for (String beanName : beanNames) {
                Object bean = applicationContext.getBean(beanName);
                log.info("  - Bean: {} -> Class: {}", beanName, bean.getClass().getSimpleName());
            }
        };
    }
}
