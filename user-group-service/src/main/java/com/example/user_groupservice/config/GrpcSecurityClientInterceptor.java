package com.example.user_groupservice.config;

import com.example.user_groupservice.web.CorrelationIdFilter;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@GrpcGlobalClientInterceptor
public class GrpcSecurityClientInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION =
        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> REQUEST_ID =
        Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> method,
        CallOptions callOptions,
        Channel next
    ) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String bearerToken = resolveBearerToken();
                if (bearerToken == null) {
                    throw new IllegalStateException("Missing caller JWT for internal gRPC call");
                }

                headers.put(AUTHORIZATION, bearerToken);

                String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
                if (requestId != null && !requestId.isBlank()) {
                    headers.put(REQUEST_ID, requestId);
                }

                super.start(responseListener, headers);
            }
        };
    }

    private String resolveBearerToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return "Bearer " + jwtAuthenticationToken.getToken().getTokenValue();
        }

        var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            String authorizationHeader = servletRequestAttributes.getRequest().getHeader("Authorization");
            if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                return authorizationHeader;
            }
        }

        return null;
    }
}