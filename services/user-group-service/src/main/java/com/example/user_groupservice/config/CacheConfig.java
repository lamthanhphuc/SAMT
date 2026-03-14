package com.example.user_groupservice.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
public class CacheConfig {

    public static final String SEMESTER_BY_ID_CACHE = "semesterById";
    public static final String SEMESTER_BY_CODE_CACHE = "semesterByCode";
    public static final String ACTIVE_SEMESTER_CACHE = "activeSemester";
    public static final String SEMESTER_LIST_CACHE = "semesterList";

    @Bean
    RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(ObjectMapper objectMapper) {
        ObjectMapper redisObjectMapper = objectMapper.copy()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        redisObjectMapper.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Object.class)
                .build(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        GenericJackson2JsonRedisSerializer.registerNullValueSerializer(redisObjectMapper, "@class");

        RedisSerializationContext.SerializationPair<Object> serializer =
            RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper));

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(serializer)
            .disableCachingNullValues();

        return builder -> builder
            .enableStatistics()
            .cacheDefaults(defaultConfig.entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration(SEMESTER_BY_ID_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(10)))
            .withCacheConfiguration(SEMESTER_BY_CODE_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(10)))
            .withCacheConfiguration(ACTIVE_SEMESTER_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(2)))
            .withCacheConfiguration(SEMESTER_LIST_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(5)));
    }
}