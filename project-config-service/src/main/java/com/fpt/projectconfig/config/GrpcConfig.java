package com.fpt.projectconfig.config;

import com.fpt.projectconfig.grpc.interceptor.AuthenticationInterceptor;
import com.fpt.projectconfig.grpc.interceptor.ServiceAuthInterceptor;
import io.grpc.ServerInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC Server Configuration
 * 
 * NOTE: User sẽ tự setup gRPC server boilerplate sau
 * File này chỉ provide các interceptors và config cần thiết
 * 
 * Để enable gRPC server, cần:
 * 1. Add dependency: net.devh:grpc-server-spring-boot-starter
 * 2. Configure trong application.yml:
 *    grpc:
 *      server:
 *        port: 9090
 * 3. Annotate service với @GrpcService
 */
@Configuration
@RequiredArgsConstructor
public class GrpcConfig {

    private final ServiceAuthInterceptor serviceAuthInterceptor;

    /**
     * Authentication interceptor bean
     */
    @Bean
    public ServerInterceptor authenticationInterceptor() {
        return new AuthenticationInterceptor();
    }

    /**
     * Service-to-service auth interceptor bean
     */
    @Bean
    public ServerInterceptor serviceToServiceInterceptor() {
        return serviceAuthInterceptor;
    }

    // TODO: Khi setup gRPC server với net.devh:grpc-server-spring-boot-starter,
    // có thể configure global interceptors như sau:
    // 
    // @Bean
    // public GlobalServerInterceptorConfigurer globalInterceptorConfigurer() {
    //     return registry -> {
    //         registry.addServerInterceptors(authenticationInterceptor());
    //         registry.addServerInterceptors(serviceToServiceInterceptor());
    //     };
    // }
}
