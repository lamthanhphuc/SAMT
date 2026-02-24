package com.example.gateway.filter;

import com.example.gateway.util.SignatureUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SignedHeaderFilter implements WebFilter, Ordered {

    private final SignatureUtil signatureUtil;

    @Override
    public int getOrder() {
        return OrderedFilters.SIGNED_HEADER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .flatMap(authentication -> {
                    Object principal = authentication.getPrincipal();
                    if (!(principal instanceof Long userId)) {
                        return chain.filter(exchange);
                    }

                    String role = authentication.getAuthorities()
                            .stream()
                            .findFirst()
                            .map(a -> a.getAuthority())
                            .orElse(null);

                    long timestamp = System.currentTimeMillis();

                    String signature =
                            signatureUtil.generateSignature(
                                    userId,
                                    role,
                                    timestamp
                            );

                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(
                                    exchange.getRequest().mutate()
                                            .header("X-User-Id", String.valueOf(userId))
                                            .header("X-User-Role", role)
                                            .header("X-Timestamp", String.valueOf(timestamp))
                                            .header("X-Internal-Signature", signature)
                                            .build()
                            )
                            .build();

                    return chain.filter(mutatedExchange);
                })
                .switchIfEmpty(chain.filter(exchange));
    }
}