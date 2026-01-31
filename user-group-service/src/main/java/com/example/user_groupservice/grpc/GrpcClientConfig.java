package com.example.user_groupservice.grpc;

import com.samt.identity.grpc.UserGrpcServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * gRPC configuration for Identity Service client.
 * Configures connection, timeout, and retry settings.
 */
@Configuration
public class GrpcClientConfig {
    
    @Value("${grpc.identity-service.host:localhost}")
    private String identityServiceHost;
    
    @Value("${grpc.identity-service.port:9090}")
    private int identityServicePort;
    
    @Value("${grpc.identity-service.deadline-seconds:5}")
    private long deadlineSeconds;
    
    /**
     * Create gRPC managed channel for Identity Service.
     * 
     * @return ManagedChannel configured for Identity Service
     */
    @Bean
    public ManagedChannel identityServiceChannel() {
        return ManagedChannelBuilder
                .forAddress(identityServiceHost, identityServicePort)
                .usePlaintext() // TODO: Use TLS in production
                .build();
    }
    
    /**
     * Create blocking stub for UserGrpcService.
     * 
     * @param channel ManagedChannel for Identity Service
     * @return UserGrpcServiceBlockingStub with deadline
     */
    @Bean
    public UserGrpcServiceGrpc.UserGrpcServiceBlockingStub userGrpcStub(ManagedChannel identityServiceChannel) {
        return UserGrpcServiceGrpc.newBlockingStub(identityServiceChannel)
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
    }
}
