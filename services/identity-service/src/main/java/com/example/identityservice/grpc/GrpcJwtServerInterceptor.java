package com.example.identityservice.grpc;

import com.example.identityservice.entity.User;
import com.example.identityservice.repository.UserRepository;
import com.example.identityservice.service.JwtService;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.stereotype.Component;

/**
 * Enforces Bearer JWT authentication on inbound gRPC calls.
 *
 * Uses the same token verification flow as HTTP filter-based auth:
 * signature/claims validation, expiration check, and active-user lookup.
 */
@Component
@GrpcGlobalServerInterceptor
public class GrpcJwtServerInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final GatewayInternalJwtValidator gatewayInternalJwtValidator;

    public GrpcJwtServerInterceptor(
            JwtService jwtService,
            UserRepository userRepository,
            GatewayInternalJwtValidator gatewayInternalJwtValidator) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.gatewayInternalJwtValidator = gatewayInternalJwtValidator;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        String fullMethodName = call.getMethodDescriptor().getFullMethodName();
        if (isUnprotectedControlMethod(fullMethodName)) {
            return next.startCall(call, headers);
        }

        String authHeader = headers.get(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing Bearer token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        String token = authHeader.substring(7);
        Long userId = extractUserId(token);
        if (userId == null) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid or expired token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        boolean activeUser = userRepository.findById(userId)
                .map(user -> user.getStatus() == User.Status.ACTIVE)
                .orElse(false);

        if (!activeUser) {
            call.close(Status.UNAUTHENTICATED.withDescription("Token user is inactive or missing"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        return next.startCall(call, headers);
    }

    private Long extractUserId(String token) {
        try {
            if (jwtService.validateToken(token) && !jwtService.isTokenExpired(token)) {
                return jwtService.extractUserId(token);
            }
        } catch (RuntimeException ignored) {
            // Fall through to gateway internal JWT validation.
        }

        return gatewayInternalJwtValidator.validateAndExtractUserId(token);
    }

    private boolean isUnprotectedControlMethod(String fullMethodName) {
        return fullMethodName != null
                && (fullMethodName.startsWith("grpc.health.v1.Health/")
                || fullMethodName.startsWith("grpc.reflection.v1alpha.ServerReflection/")
                || fullMethodName.startsWith("grpc.reflection.v1.ServerReflection/"));
    }
}
