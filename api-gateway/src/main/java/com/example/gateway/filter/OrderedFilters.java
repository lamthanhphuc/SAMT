package com.example.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;

public class OrderedFilters {
    public static final int ORDER_CORS = 0;
    public static final int ORDER_RATELIMITER = 1;
    public static final int ORDER_JWT = 2;
    public static final int ORDER_SIGNED_HEADER = 3;
    public static final int ORDER_CIRCUIT_BREAKER = 4;
    public static final int ORDER_RETRY = 5;
    public static final int ORDER_TIMEOUT = 6;
    public static final int ORDER_FORWARD = 7;

    public static GatewayFilter cors(GatewayFilter filter) {
        return new OrderedGatewayFilter(filter, ORDER_CORS);
    }
    public static GatewayFilter rateLimiter(GatewayFilter filter) {
        return new OrderedGatewayFilter(filter, ORDER_RATELIMITER);
    }
    public static GatewayFilter jwt(GatewayFilter filter) {
        return new OrderedGatewayFilter(filter, ORDER_JWT);
    }
    public static GatewayFilter signedHeader(GatewayFilter filter) {
        return new OrderedGatewayFilter(filter, ORDER_SIGNED_HEADER);
    }
    public static GatewayFilter circuitBreaker(GatewayFilter filter) {
        return new OrderedGatewayFilter(filter, ORDER_CIRCUIT_BREAKER);
    }
    public static GatewayFilter retry(GatewayFilter filter) {
        return new OrderedGatewayFilter(filter, ORDER_RETRY);
    }
    public static GatewayFilter timeout(GatewayFilter filter) {
        return new OrderedGatewayFilter(filter, ORDER_TIMEOUT);
    }
    public static GatewayFilter forward(GatewayFilter filter) {
        return new OrderedGatewayFilter(filter, ORDER_FORWARD);
    }
}
