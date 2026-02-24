package com.example.gateway.config;

import com.example.gateway.resolver.IpKeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {
    @Bean
    public IpKeyResolver ipKeyResolver() {
        return new IpKeyResolver();
    }
}
