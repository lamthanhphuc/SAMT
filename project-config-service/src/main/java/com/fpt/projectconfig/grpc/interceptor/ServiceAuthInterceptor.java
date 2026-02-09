package com.fpt.projectconfig.grpc.interceptor;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Service-to-Service authentication interceptor cho internal methods
 * Validate X-Service-Name và X-Service-Key từ metadata
 * 
 * Apply cho các internal methods như InternalGetDecryptedConfig
 */
@Component
@Slf4j
public class ServiceAuthInterceptor implements ServerInterceptor {

    private final String expectedServiceKey;

    public ServiceAuthInterceptor(@Value("${security.internal-service-key}") String serviceKey) {
        this.expectedServiceKey = serviceKey;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();
        
        // Chỉ apply cho internal methods
        if (!methodName.contains("Internal")) {
            return next.startCall(call, headers);
        }

        String serviceName = headers.get(Metadata.Key.of("x-service-name", Metadata.ASCII_STRING_MARSHALLER));
        String serviceKey = headers.get(Metadata.Key.of("x-service-key", Metadata.ASCII_STRING_MARSHALLER));

        if (serviceName == null || serviceKey == null || !expectedServiceKey.equals(serviceKey)) {
            log.warn("Invalid service authentication for method: {}", methodName);
            call.close(Status.PERMISSION_DENIED.withDescription("Invalid service credentials"), new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }

        log.debug("Service authenticated: {} for method: {}", serviceName, methodName);
        return next.startCall(call, headers);
    }
}
