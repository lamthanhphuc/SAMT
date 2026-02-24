package com.example.syncservice.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for WebClient instances.
 * 
 * CRITICAL: WebClient is non-blocking and suitable for external API calls.
 * Each client has proper timeouts to prevent hanging requests.
 */
@Configuration
public class WebClientConfig {

    /**
     * WebClient for Jira API calls.
     * 
     * Timeout: 30 seconds (Jira can be slow)
     * Connection pool: Spring Boot default (500 connections)
     */
    @Bean("jiraWebClient")
    public WebClient jiraWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)) // 16MB
                .build();
    }

    /**
     * WebClient for GitHub API calls.
     * 
     * Base URL: https://api.github.com
     * Timeout: 20 seconds
     */
    @Bean("githubWebClient")
    public WebClient githubWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(20))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(20, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(20, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl("https://api.github.com")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }
}
