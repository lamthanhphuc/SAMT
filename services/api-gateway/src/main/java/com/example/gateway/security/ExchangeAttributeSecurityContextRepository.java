package com.example.gateway.security;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class ExchangeAttributeSecurityContextRepository implements ServerSecurityContextRepository {

    public static final String ATTRIBUTE_NAME = ExchangeAttributeSecurityContextRepository.class.getName() + ".SECURITY_CONTEXT";

    @Override
    public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
        exchange.getAttributes().put(ATTRIBUTE_NAME, context);
        return Mono.empty();
    }

    @Override
    public Mono<SecurityContext> load(ServerWebExchange exchange) {
        Object context = exchange.getAttribute(ATTRIBUTE_NAME);
        if (context instanceof SecurityContext securityContext) {
            return Mono.just(securityContext);
        }
        return Mono.empty();
    }
}