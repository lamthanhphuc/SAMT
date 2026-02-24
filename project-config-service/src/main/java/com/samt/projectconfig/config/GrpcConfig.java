package com.samt.projectconfig.config;

import com.example.project_configservice.grpc.UserGroupGrpcServiceGrpc;
import io.grpc.ManagedChannel;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC Configuration for async non-blocking stubs.
 * 
 * Provides UserGroupFutureStub for async gRPC calls to User-Group Service.
 * 
 * Benefits:
 * - Non-blocking I/O: HTTP threads not blocked during gRPC calls
 * - Event-loop based: gRPC runs on Netty event loop
 * - CompletableFuture based: Integrates with async service layer
 * 
 * @author Production Team
 * @version 2.0 (Async refactored)
 */
@Configuration
public class GrpcConfig {
    
    @GrpcClient("user-group-service")
    private ManagedChannel managedChannel;
    
    /**
     * Provides UserGroupFutureStub for non-blocking async gRPC calls.
     * 
     * FutureStub returns ListenableFuture which can be converted to CompletableFuture.
     * All gRPC calls are non-blocking and execute on gRPC's Netty event loop.
     * 
     * @return UserGroupFutureStub for async gRPC calls
     */
    @Bean
    public UserGroupGrpcServiceGrpc.UserGroupGrpcServiceFutureStub userGroupFutureStub() {
        return UserGroupGrpcServiceGrpc.newFutureStub(managedChannel);
    }
}
