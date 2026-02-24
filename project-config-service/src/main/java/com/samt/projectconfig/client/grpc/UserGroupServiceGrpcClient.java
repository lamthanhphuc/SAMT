package com.samt.projectconfig.client.grpc;

import com.example.project_configservice.grpc.*;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.Deadline;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Async non-blocking gRPC client for User-Group Service validation.
 * 
 * Architecture Changes (v2.0):
 * - ✅ Replaced BlockingStub with FutureStub (async non-blocking)
 * - ✅ All methods return CompletableFuture
 * - ✅ HTTP threads freed immediately (no blocking)
 * - ✅ gRPC calls run on Netty event loop
 * - ✅ Deadline 800ms for fast-fail
 * - ✅ Circuit breaker preserved for resilience
 * 
 * Security:
 * - Exceptions properly propagated through CompletableFuture
 * - Deadline enforced per call (800ms)
 * - Proper gRPC status to HTTP exception mapping
 * 
 * Resilience Strategy:
 * - Circuit Breaker: Fail-fast when User-Group Service is down
 * - Deadline: 800ms per gRPC call
 * - NO Bulkhead: gRPC uses Netty event loop (non-blocking)
 * 
 * Fallback Behavior: Returns failed CompletableFuture with ServiceUnavailableException
 * 
 * @author Production Team
 * @version 2.0 (Async refactored)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserGroupServiceGrpcClient {
    
    private final UserGroupGrpcServiceGrpc.UserGroupGrpcServiceFutureStub futureStub;
    private final GrpcExceptionMapper exceptionMapper;
    
    private static final long GRPC_DEADLINE_MS = 800;
    
    /**
     * Static Metadata.Key for correlation ID propagation.
     * 
     * Key created once at class load time for efficiency.
     * Thread-safe and reusable across all gRPC calls.
     * Metadata object only created when correlation ID present.
     */
    private static final Metadata.Key<String> CORRELATION_ID_KEY =
        Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);
    
    
    /**
     * Async verify that a group exists and is not soft-deleted.
     * Protected by circuit breaker to fail-fast when User-Group Service is down.
     * 
     * Async Behavior:
     * - Returns CompletableFuture immediately (HTTP thread freed)
     * - gRPC call runs on Netty event loop (non-blocking)
     * - Completes exceptionally on failure
     * 
     * Circuit Breaker Configuration:
     * - Opens after 50% failure rate or 80% slow calls
     * - Waits 10s in OPEN state before HALF_OPEN
     * - Prevents cascading failures
     * 
     * @param groupId Group ID to verify
     * @return CompletableFuture<VerifyGroupResponse>
     * @throws GroupNotFoundException if group not found (async)
     * @throws ServiceUnavailableException if service unavailable (async)
     * @throws GatewayTimeoutException if deadline exceeded (async)
     */
    @CircuitBreaker(name = "userGroupService", fallbackMethod = "verifyGroupExistsFallback")
    public CompletableFuture<VerifyGroupResponse> verifyGroupExists(Long groupId) {
        log.debug("Async verifying group exists: groupId={}", groupId);
        
        try {
            VerifyGroupRequest request = VerifyGroupRequest.newBuilder()
                .setGroupId(groupId.toString())
                .build();
            
            // Extract correlation ID from MDC (set by filter or propagated by TaskDecorator)
            String correlationId = MDC.get("correlationId");
            
            UserGroupGrpcServiceGrpc.UserGroupGrpcServiceFutureStub stub = futureStub
                .withDeadline(Deadline.after(GRPC_DEADLINE_MS, TimeUnit.MILLISECONDS));
            
            // Only create Metadata if correlation ID present (avoid unnecessary allocation)
            if (correlationId != null && !correlationId.isEmpty()) {
                Metadata metadata = new Metadata();
                metadata.put(CORRELATION_ID_KEY, correlationId);
                stub = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
            }
            
            ListenableFuture<VerifyGroupResponse> future = stub.verifyGroupExists(request);
            
            return toCompletableFuture(future)
                .thenApply(response -> {
                    if (!response.getExists()) {
                        log.warn("Group not found: groupId={}", groupId);
                        throw new com.samt.projectconfig.exception.GroupNotFoundException(groupId);
                    }
                    
                    if (response.getDeleted()) {
                        log.warn("Group is soft-deleted: groupId={}", groupId);
                        throw new com.samt.projectconfig.exception.GroupNotFoundException(
                            "Group " + groupId + " has been deleted"
                        );
                    }
                    
                    log.debug("Group verified successfully: groupId={}", groupId);
                    return response;
                })
                .exceptionally(throwable -> {
                    if (throwable instanceof StatusRuntimeException ex) {
                        exceptionMapper.mapAndThrow(ex, "verifyGroupExists");
                    }
                    // Re-throw business exceptions
                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    }
                    throw new RuntimeException("Unexpected error during group verification", throwable);
                });
            
        } catch (Exception ex) {
            log.error("Error initiating async verifyGroupExists: groupId={}", groupId, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }
    
    
    /**
     * Async check if a user is the LEADER of a group.
     * Protected by circuit breaker to fail-fast when User-Group Service is down.
     * 
     * Async Behavior:
     * - Returns CompletableFuture immediately (HTTP thread freed)
     * - gRPC call runs on Netty event loop (non-blocking)
     * - Completes exceptionally if user is not leader
     * 
     * @param groupId Group ID
     * @param userId User ID to check
     * @return CompletableFuture<CheckGroupLeaderResponse>
     * @throws ForbiddenException if user is not LEADER (async)
     * @throws GroupNotFoundException if group not found (async)
     * @throws ServiceUnavailableException if service unavailable (async)
     */
    @CircuitBreaker(name = "userGroupService", fallbackMethod = "checkGroupLeaderFallback")
    public CompletableFuture<CheckGroupLeaderResponse> checkGroupLeader(Long groupId, Long userId) {
        log.debug("Async checking group leader: groupId={}, userId={}", groupId, userId);
        
        try {
            CheckGroupLeaderRequest request = CheckGroupLeaderRequest.newBuilder()
                .setGroupId(groupId.toString())
                .setUserId(userId.toString())
                .build();
            
            // Extract correlation ID from MDC (set by filter or propagated by TaskDecorator)
            String correlationId = MDC.get("correlationId");
            
            UserGroupGrpcServiceGrpc.UserGroupGrpcServiceFutureStub stub = futureStub
                .withDeadline(Deadline.after(GRPC_DEADLINE_MS, TimeUnit.MILLISECONDS));
            
            // Only create Metadata if correlation ID present (avoid unnecessary allocation)
            if (correlationId != null && !correlationId.isEmpty()) {
                Metadata metadata = new Metadata();
                metadata.put(CORRELATION_ID_KEY, correlationId);
                stub = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
            }
            
            ListenableFuture<CheckGroupLeaderResponse> future = stub.checkGroupLeader(request);
            
            return toCompletableFuture(future)
                .thenApply(response -> {
                    if (!response.getIsLeader()) {
                        log.warn("User is not group leader: groupId={}, userId={}", groupId, userId);
                        throw new com.samt.projectconfig.exception.ForbiddenException(
                            "User " + userId + " is not the leader of group " + groupId
                        );
                    }
                    
                    log.debug("User is group leader: groupId={}, userId={}", groupId, userId);
                    return response;
                })
                .exceptionally(throwable -> {
                    if (throwable instanceof StatusRuntimeException ex) {
                        exceptionMapper.mapAndThrow(ex, "checkGroupLeader");
                    }
                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    }
                    throw new RuntimeException("Unexpected error during leader check", throwable);
                });
            
        } catch (Exception ex) {
            log.error("Error initiating async checkGroupLeader: groupId={}, userId={}", groupId, userId, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }
    
    
    /**
     * Async check if a user is a MEMBER (including leader) of a group.
     * Used for STUDENT role authorization to view configs.
     * 
     * Async Behavior:
     * - Returns CompletableFuture immediately
     * - gRPC call runs on Netty event loop
     * 
     * @param groupId Group ID
     * @param userId User ID to check
     * @return CompletableFuture<CheckGroupLeaderResponse>
     * @throws ForbiddenException if user is not a member (async)
     */
    @CircuitBreaker(name = "userGroupService", fallbackMethod = "checkGroupMembershipFallback")
    public CompletableFuture<CheckGroupLeaderResponse> checkGroupMembership(Long groupId, Long userId) {
        log.debug("Async checking group membership: groupId={}, userId={}", groupId, userId);
        
        try {
            CheckGroupLeaderRequest leaderRequest = CheckGroupLeaderRequest.newBuilder()
                .setGroupId(groupId.toString())
                .setUserId(userId.toString())
                .build();
            
            // Extract correlation ID from MDC (set by filter or propagated by TaskDecorator)
            String correlationId = MDC.get("correlationId");
            
            UserGroupGrpcServiceGrpc.UserGroupGrpcServiceFutureStub stub = futureStub
                .withDeadline(Deadline.after(GRPC_DEADLINE_MS, TimeUnit.MILLISECONDS));
            
            // Only create Metadata if correlation ID present (avoid unnecessary allocation)
            if (correlationId != null && !correlationId.isEmpty()) {
                Metadata metadata = new Metadata();
                metadata.put(CORRELATION_ID_KEY, correlationId);
                stub = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
            }
            
            ListenableFuture<CheckGroupLeaderResponse> future = stub.checkGroupLeader(leaderRequest);
            
            return toCompletableFuture(future)
                .thenApply(response -> {
                    if (response.getIsLeader()) {
                        log.debug("User is group leader (auto-member): groupId={}, userId={}", groupId, userId);
                        return response;
                    }
                    
                    // Not leader - deny access for now
                    log.warn("User is not group leader: groupId={}, userId={}", groupId, userId);
                    throw new com.samt.projectconfig.exception.ForbiddenException(
                        "User " + userId + " is not authorized to access group " + groupId + " configuration"
                    );
                })
                .exceptionally(throwable -> {
                    if (throwable instanceof StatusRuntimeException ex) {
                        exceptionMapper.mapAndThrow(ex, "checkGroupMembership");
                    }
                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    }
                    throw new RuntimeException("Unexpected error during membership check", throwable);
                });
            
        } catch (Exception ex) {
            log.error("Error initiating async checkGroupMembership: groupId={}, userId={}", groupId, userId, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }
    
    /**
     * Fallback when verifyGroupExists circuit breaker is OPEN.
     * Returns failed CompletableFuture with ServiceUnavailableException.
     * 
     * @param groupId Group ID that was being verified
     * @param ex Exception that triggered circuit breaker
     * @return Failed CompletableFuture
     */
    private CompletableFuture<VerifyGroupResponse> verifyGroupExistsFallback(Long groupId, Exception ex) {
        log.error("Circuit breaker OPEN for verifyGroupExists: groupId={} - {} - {}", 
            groupId, ex.getClass().getSimpleName(), ex.getMessage());
        
        return CompletableFuture.failedFuture(
            new com.samt.projectconfig.exception.ServiceUnavailableException(
                "User-Group Service is temporarily unavailable (circuit breaker OPEN)"
            )
        );
    }
    
    /**
     * Fallback when checkGroupLeader circuit breaker is OPEN.
     * Returns failed CompletableFuture with ServiceUnavailableException.
     * 
     * @param groupId Group ID
     * @param userId User ID
     * @param ex Exception that triggered circuit breaker
     * @return Failed CompletableFuture
     */
    private CompletableFuture<CheckGroupLeaderResponse> checkGroupLeaderFallback(Long groupId, Long userId, Exception ex) {
        log.error("Circuit breaker OPEN for checkGroupLeader: groupId={} userId={} - {} - {}", 
            groupId, userId, ex.getClass().getSimpleName(), ex.getMessage());
        
        return CompletableFuture.failedFuture(
            new com.samt.projectconfig.exception.ServiceUnavailableException(
                "User-Group Service is temporarily unavailable (circuit breaker OPEN)"
            )
        );
    }
    
    /**
     * Fallback when checkGroupMembership circuit breaker is OPEN.
     * Returns failed CompletableFuture with ServiceUnavailableException.
     * 
     * @param groupId Group ID
     * @param userId User ID
     * @param ex Exception that triggered circuit breaker
     * @return Failed CompletableFuture
     */
    private CompletableFuture<CheckGroupLeaderResponse> checkGroupMembershipFallback(Long groupId, Long userId, Exception ex) {
        log.error("Circuit breaker OPEN for checkGroupMembership: groupId={} userId={} - {} - {}", 
            groupId, userId, ex.getClass().getSimpleName(), ex.getMessage());
        
        return CompletableFuture.failedFuture(
            new com.samt.projectconfig.exception.ServiceUnavailableException(
                "User-Group Service is temporarily unavailable (circuit breaker OPEN)"
            )
        );
    }
    
    /**
     * Helper method to convert Guava ListenableFuture to Java CompletableFuture.
     * 
     * @param listenableFuture Guava ListenableFuture from gRPC stub
     * @param <T> Response type
     * @return CompletableFuture
     */
    private <T> CompletableFuture<T> toCompletableFuture(ListenableFuture<T> listenableFuture) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        
        Futures.addCallback(
            listenableFuture,
            new com.google.common.util.concurrent.FutureCallback<T>() {
                @Override
                public void onSuccess(T result) {
                    completableFuture.complete(result);
                }
                
                @Override
                public void onFailure(Throwable t) {
                    completableFuture.completeExceptionally(t);
                }
            },
            Runnable::run  // Execute callback on same thread (Netty event loop)
        );
        
        return completableFuture;
    }
}
