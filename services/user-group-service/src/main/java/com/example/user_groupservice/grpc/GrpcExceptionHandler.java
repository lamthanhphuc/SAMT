package com.example.user_groupservice.grpc;

import com.example.user_groupservice.exception.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Centralized gRPC exception handler that maps gRPC StatusRuntimeException
 * to appropriate HTTP exceptions according to API_CONTRACT.md specification.
 * 
 * <p>gRPC Status → HTTP Status mapping:
 * <ul>
 *   <li>NOT_FOUND → 404 ResourceNotFoundException</li>
 *   <li>PERMISSION_DENIED → 403 ForbiddenException</li>
 *   <li>UNAVAILABLE → 503 ServiceUnavailableException</li>
 *   <li>DEADLINE_EXCEEDED → 504 GatewayTimeoutException</li>
 *   <li>INVALID_ARGUMENT → 400 BadRequestException</li>
 *   <li>FAILED_PRECONDITION → 409 ConflictException</li>
 *   <li>UNAUTHENTICATED → 401 UnauthorizedException</li>
 *   <li>Others → 500 RuntimeException</li>
 * </ul>
 */
@Component
@Slf4j
public class GrpcExceptionHandler {
    
    /**
     * Execute a gRPC call with automatic exception mapping.
     * 
     * @param grpcCall The gRPC call to execute
     * @param operationName Descriptive name for logging (e.g., "verifyLecturer", "getUser")
     * @param <T> Return type of the gRPC call
     * @return The result of the gRPC call
     * @throws ResourceNotFoundException if gRPC returns NOT_FOUND
     * @throws ForbiddenException if gRPC returns PERMISSION_DENIED
     * @throws ServiceUnavailableException if gRPC returns UNAVAILABLE
     * @throws GatewayTimeoutException if gRPC returns DEADLINE_EXCEEDED
     * @throws BadRequestException if gRPC returns INVALID_ARGUMENT
     * @throws ConflictException if gRPC returns FAILED_PRECONDITION
     * @throws UnauthorizedException if gRPC returns UNAUTHENTICATED
     * @throws RuntimeException for other gRPC errors
     */
    public <T> T handleGrpcCall(Supplier<T> grpcCall, String operationName) {
        try {
            return grpcCall.get();
        } catch (StatusRuntimeException e) {
            throw mapGrpcException(e, operationName);
        }
    }
    
    /**
     * Map gRPC StatusRuntimeException to appropriate HTTP exception.
     * 
     * @param e The gRPC exception
     * @param operation Operation name for error messages
     * @return Mapped exception (always throws, never returns)
     */
    private RuntimeException mapGrpcException(StatusRuntimeException e, String operation) {
        Status.Code code = e.getStatus().getCode();
        String description = e.getStatus().getDescription();
        
        log.error("gRPC call failed [operation={}, status={}, description={}]", 
            operation, code, description != null ? description : "N/A");
        
        return switch (code) {
            case NOT_FOUND -> new ResourceNotFoundException(
                "RESOURCE_NOT_FOUND", 
                "Resource not found in Identity Service: " + operation
            );
            
            case PERMISSION_DENIED -> new ForbiddenException(
                "FORBIDDEN",
                "Access denied by Identity Service: " + operation
            );
            
            case UNAVAILABLE -> ServiceUnavailableException.identityServiceUnavailable();
            
            case DEADLINE_EXCEEDED -> GatewayTimeoutException.identityServiceTimeout();
            
            case INVALID_ARGUMENT -> new BadRequestException(
                "BAD_REQUEST",
                description != null ? description : "Invalid request to Identity Service: " + operation
            );
            
            case FAILED_PRECONDITION -> new ConflictException(
                "CONFLICT",
                description != null ? description : "Business rule violation in Identity Service: " + operation
            );
            
            case UNAUTHENTICATED -> new UnauthorizedException(
                description != null ? description : "Authentication failed with Identity Service: " + operation
            );
            
            default -> {
                log.error("Unexpected gRPC error code [operation={}, code={}]", operation, code);
                yield new RuntimeException(
                    "Unexpected Identity Service error [" + code + "]: " + 
                    (description != null ? description : operation)
                );
            }
        };
    }
}
