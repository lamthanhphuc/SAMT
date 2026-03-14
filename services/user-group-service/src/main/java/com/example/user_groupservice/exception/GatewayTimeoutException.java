package com.example.user_groupservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for gRPC DEADLINE_EXCEEDED errors.
 * Returns HTTP 504 GATEWAY_TIMEOUT.
 */
public class GatewayTimeoutException extends BaseException {
    
    public GatewayTimeoutException(String message) {
        super("GATEWAY_TIMEOUT", message, HttpStatus.GATEWAY_TIMEOUT);
    }
    
    public static GatewayTimeoutException identityServiceTimeout() {
        return new GatewayTimeoutException("Identity Service request timed out");
    }
}
