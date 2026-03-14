package com.samt.projectconfig.exception;

/**
 * Exception thrown when gRPC call exceeds deadline.
 * Maps to HTTP 504.
 */
public class GatewayTimeoutException extends RuntimeException {
    
    public GatewayTimeoutException(String service) {
        super(service + " request timeout");
    }
    
    public GatewayTimeoutException(String service, Throwable cause) {
        super(service + " request timeout", cause);
    }
}
