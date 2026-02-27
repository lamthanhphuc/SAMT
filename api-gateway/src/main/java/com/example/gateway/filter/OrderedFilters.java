package com.example.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;

public class OrderedFilters {
    private OrderedFilters() {}

    public static final int CORRELATION_ID = -250;  // Run first for request tracing
    public static final int CORS = -200;
    public static final int RATE_LIMIT = -150;
    public static final int JWT = -100;
    public static final int SIGNED_HEADER = -50;

   
}
