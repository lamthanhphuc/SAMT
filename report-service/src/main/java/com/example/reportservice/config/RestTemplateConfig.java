package com.example.reportservice.config;



import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(InternalServiceProperties.class)
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(AiServiceProperties properties) {
        int timeout = properties.getTimeout() > 0 ? properties.getTimeout() : 5000;

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);

        return new RestTemplate(factory);
    }
}
