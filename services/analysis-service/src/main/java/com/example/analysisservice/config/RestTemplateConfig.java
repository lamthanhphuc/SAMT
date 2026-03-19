package com.example.analysisservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(LocalAiProperties localAiProperties) {
        var factory = new SimpleClientHttpRequestFactory();
        // Ollama can legitimately take minutes for extraction + writing. Keep this aligned with AI_TIMEOUT_MS.
        int timeoutMs = Math.max(10_000, localAiProperties.getTimeoutMs());
        factory.setConnectTimeout(Math.min(10_000, timeoutMs));
        factory.setReadTimeout(timeoutMs);

        return new RestTemplate(factory);
    }
}
