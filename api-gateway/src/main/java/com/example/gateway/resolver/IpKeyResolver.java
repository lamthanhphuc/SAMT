package com.example.gateway.resolver;

import com.example.gateway.service.IpResolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * IP-based key resolver for rate limiting with trusted proxy support.
 * Uses shared IpResolutionService for consistent IP resolution across gateway components.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IpKeyResolver implements KeyResolver {
    
    private final IpResolutionService ipResolutionService;
    
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        try {
            String clientIp = ipResolutionService.resolveClientIp(exchange.getRequest());
            log.debug("Rate limiting key resolved: {}", clientIp);
            return Mono.just(clientIp);
        } catch (Exception e) {
            // FAIL-CLOSED: Reject request when rate limiter unavailable
            log.error("Rate limiter unavailable, rejecting request for security: {}", e.getMessage());
            return Mono.error(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, 
                "Rate limiting service temporarily unavailable"
            ));
        }
    }
}
