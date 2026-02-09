package com.fpt.projectconfig.grpc.interceptor;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC Interceptor để extract userId và roles từ metadata
 * 
 * Assumption: Client sẽ gửi metadata:
 * - "user-id": Long userId từ JWT
 * - "roles": Comma-separated roles (ADMIN,LECTURER,STUDENT)
 * 
 * NOTE: Authentication/JWT validation nên được xử lý ở API Gateway
 * Service này chỉ trust metadata từ gateway
 */
@Slf4j
public class AuthenticationInterceptor implements ServerInterceptor {

    private static final Context.Key<Long> USER_ID_KEY = Context.key("userId");
    private static final Context.Key<String> ROLES_KEY = Context.key("roles");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        try {
            // Extract userId từ metadata
            String userIdStr = headers.get(Metadata.Key.of("user-id", Metadata.ASCII_STRING_MARSHALLER));
            String roles = headers.get(Metadata.Key.of("roles", Metadata.ASCII_STRING_MARSHALLER));

            if (userIdStr == null || roles == null) {
                log.warn("Missing authentication metadata for method: {}", call.getMethodDescriptor().getFullMethodName());
                call.close(Status.UNAUTHENTICATED.withDescription("Missing user-id or roles"), new Metadata());
                return new ServerCall.Listener<ReqT>() {};
            }

            Long userId = Long.parseLong(userIdStr);
            
            // Set vào Context để service methods có thể access
            Context context = Context.current()
                    .withValue(USER_ID_KEY, userId)
                    .withValue(ROLES_KEY, roles);

            log.debug("Authenticated user: {} with roles: {}", userId, roles);

            return Contexts.interceptCall(context, call, headers, next);

        } catch (NumberFormatException e) {
            log.error("Invalid user-id format", e);
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid user-id format"), new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }
    }

    /**
     * Get userId từ current gRPC context
     */
    public static Long getUserId() {
        Long userId = USER_ID_KEY.get();
        if (userId == null) {
            throw Status.UNAUTHENTICATED.withDescription("No user context").asRuntimeException();
        }
        return userId;
    }

    /**
     * Get roles từ current gRPC context
     */
    public static String[] getRoles() {
        String roles = ROLES_KEY.get();
        if (roles == null) {
            throw Status.UNAUTHENTICATED.withDescription("No roles context").asRuntimeException();
        }
        return roles.split(",");
    }
}
