package com.samt.projectconfig.client.grpc;

import com.samt.projectconfig.exception.BadRequestException;
import com.samt.projectconfig.exception.GatewayTimeoutException;
import com.samt.projectconfig.exception.GroupNotFoundException;
import com.samt.projectconfig.exception.ServiceUnavailableException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Maps gRPC status codes to appropriate HTTP exceptions.
 * 
 * Mapping:
 * - NOT_FOUND → GroupNotFoundException (404)
 * - INVALID_ARGUMENT → BadRequestException (400)
 * - UNAVAILABLE → ServiceUnavailableException (503)
 * - DEADLINE_EXCEEDED → GatewayTimeoutException (504)
 * - Others → RuntimeException (500)
 */
@Component
@Slf4j
public class GrpcExceptionMapper {
    
    private static final String SERVICE_NAME = "User-Group Service";
    
    /**
     * Maps gRPC StatusRuntimeException to appropriate application exception.
     * 
     * @param ex gRPC exception
     * @param operation Operation name for logging
     * @throws GroupNotFoundException if gRPC status is NOT_FOUND
     * @throws BadRequestException if gRPC status is INVALID_ARGUMENT
     * @throws ServiceUnavailableException if gRPC status is UNAVAILABLE
     * @throws GatewayTimeoutException if gRPC status is DEADLINE_EXCEEDED
     * @throws RuntimeException for other gRPC errors
     */
    public void mapAndThrow(StatusRuntimeException ex, String operation) {
        Status status = ex.getStatus();
        String description = status.getDescription() != null ? status.getDescription() : "";
        
        log.error("gRPC call failed: operation={}, status={}, description={}", 
                  operation, status.getCode(), description);
        
        switch (status.getCode()) {
            case NOT_FOUND:
                throw new GroupNotFoundException(description);
                
            case INVALID_ARGUMENT:
                throw new BadRequestException(description);
                
            case UNAVAILABLE:
                throw new ServiceUnavailableException(SERVICE_NAME, ex);
                
            case DEADLINE_EXCEEDED:
                throw new GatewayTimeoutException(SERVICE_NAME, ex);
                
            case PERMISSION_DENIED:
                throw new com.samt.projectconfig.exception.ForbiddenException(description);
                
            default:
                log.error("Unexpected gRPC error: {}", status, ex);
                throw new RuntimeException(SERVICE_NAME + " error: " + description, ex);
        }
    }
}
