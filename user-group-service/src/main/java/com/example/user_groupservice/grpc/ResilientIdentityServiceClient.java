package com.example.user_groupservice.grpc;

import com.example.user_groupservice.grpc.GetUserResponse;
import com.example.user_groupservice.grpc.VerifyUserResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Identity Service Client Wrapper với Circuit Breaker & Retry
 * 
 * Wrap tất cả gRPC calls tới Identity Service với:
 * - Circuit Breaker: Ngăn cascade failure khi Identity Service down
 * - Retry: Tự động retry khi gặp transient errors
 * - Fail-fast: Throw exception nhanh khi circuit is OPEN
 */
@Slf4j
@Component
public class ResilientIdentityServiceClient {

    private final IdentityServiceClient identityServiceClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ResilientIdentityServiceClient(
            IdentityServiceClient identityServiceClient,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry) {
        
        this.identityServiceClient = identityServiceClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("identityService");
        this.retry = retryRegistry.retry("identityService");
    }

    /**
     * Verify user exists với Circuit Breaker & Retry
     * 
     * @throws StatusRuntimeException nếu gRPC call fails
     * @throws io.github.resilience4j.circuitbreaker.CallNotPermittedException nếu circuit is OPEN
     */
    public VerifyUserResponse verifyUserExists(Long userId) {
        log.debug("Verifying user with resilience: {}", userId);
        
        Supplier<VerifyUserResponse> supplier = () -> {
            try {
                return identityServiceClient.verifyUserExists(userId);
            } catch (StatusRuntimeException e) {
                log.warn("Identity Service gRPC call failed: {}", e.getStatus());
                throw e;
            }
        };
        
        return circuitBreaker.executeSupplier(
                Retry.decorateSupplier(retry, supplier)
        );
    }

    /**
     * Get user info với Circuit Breaker & Retry
     */
    public GetUserResponse getUser(Long userId) {
        log.debug("Getting user info with resilience: {}", userId);
        
        Supplier<GetUserResponse> supplier = () -> {
            try {
                return identityServiceClient.getUser(userId);
            } catch (StatusRuntimeException e) {
                log.warn("Identity Service gRPC call failed: {}", e.getStatus());
                throw e;
            }
        };
        
        return circuitBreaker.executeSupplier(
                Retry.decorateSupplier(retry, supplier)
        );
    }

    /**
     * Get user role với Circuit Breaker & Retry
     */
    public GetUserRoleResponse getUserRole(Long userId) {
        log.debug("Getting user role with resilience: {}", userId);
        
        Supplier<GetUserRoleResponse> supplier = () -> {
            try {
                return identityServiceClient.getUserRole(userId);
            } catch (StatusRuntimeException e) {
                log.warn("Identity Service gRPC call failed: {}", e.getStatus());
                throw e;
            }
        };
        
        return circuitBreaker.executeSupplier(
                Retry.decorateSupplier(retry, supplier)
        );
    }

    public UpdateUserResponse updateUser(Long userId, String fullName) {
        log.debug("Updating user with resilience: {}", userId);

        Supplier<UpdateUserResponse> supplier = () -> {
            try {
                return identityServiceClient.updateUser(userId, fullName);
            } catch (StatusRuntimeException e) {
                log.warn("Identity Service gRPC call failed: {}", e.getStatus());
                throw e;
            }
        };

        return circuitBreaker.executeSupplier(
                Retry.decorateSupplier(retry, supplier)
        );
    }

    public ListUsersResponse listUsers(int page, int size, String status, String role) {
        log.debug("Listing users with resilience: page={}, size={}", page, size);

        Supplier<ListUsersResponse> supplier = () -> {
            try {
                return identityServiceClient.listUsers(page, size, status, role);
            } catch (StatusRuntimeException e) {
                log.warn("Identity Service gRPC call failed: {}", e.getStatus());
                throw e;
            }
        };

        return circuitBreaker.executeSupplier(
                Retry.decorateSupplier(retry, supplier)
        );
    }

    public GetUsersResponse getUsers(java.util.List<Long> userIds) {
        log.debug("Batch getting users with resilience: count={}", userIds != null ? userIds.size() : 0);

        Supplier<GetUsersResponse> supplier = () -> {
            try {
                return identityServiceClient.getUsers(userIds);
            } catch (StatusRuntimeException e) {
                log.warn("Identity Service gRPC call failed: {}", e.getStatus());
                throw e;
            }
        };

        return circuitBreaker.executeSupplier(
                Retry.decorateSupplier(retry, supplier)
        );
    }

    /**
     * Check if Circuit Breaker is OPEN (Identity Service unavailable)
     */
    public boolean isCircuitBreakerOpen() {
        var state = circuitBreaker.getState();
        return state == CircuitBreaker.State.OPEN || 
               state == CircuitBreaker.State.FORCED_OPEN;
    }

    /**
     * Get Circuit Breaker metrics
     */
    public CircuitBreaker.Metrics getMetrics() {
        return circuitBreaker.getMetrics();
    }

    /**
     * Get Circuit Breaker state
     */
    public CircuitBreaker.State getState() {
        return circuitBreaker.getState();
    }
}
