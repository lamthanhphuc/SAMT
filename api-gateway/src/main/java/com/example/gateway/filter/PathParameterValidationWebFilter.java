package com.example.gateway.filter;

import com.example.gateway.error.GatewayErrorResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Component
public class PathParameterValidationWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(PathParameterValidationWebFilter.class);

    private static final String DIGITS_REGEX = "\\d+";

    private final GatewayErrorResponseWriter errorResponseWriter;

    public PathParameterValidationWebFilter(GatewayErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String rawPath = exchange.getRequest().getURI().getRawPath();
        boolean hasAuthorization = exchange.getRequest().getHeaders().containsKey("Authorization");
        List<String> segments = Arrays.stream(rawPath.split("/"))
            .filter(segment -> !segment.isBlank())
            .toList();

        if (segments.size() < 2 || !"api".equals(segments.get(0))) {
            return chain.filter(exchange);
        }

        if (hasInvalidPathParameter(segments, hasAuthorization)) {
            log.warn("Rejected malformed path parameter. rawPath={}", rawPath);
            return errorResponseWriter.write(
                exchange,
                400,
                "Invalid request",
                "Invalid value for path parameter"
            );
        }

        return chain.filter(exchange);
    }

    private boolean hasInvalidPathParameter(List<String> segments, boolean hasAuthorization) {
        if (isUserGroupPathInvalid(segments)) {
            return true;
        }
        if (isUserPathInvalid(segments)) {
            return true;
        }
        if (isSemesterPathInvalid(segments)) {
            return true;
        }
        if (isAdminUserPathInvalid(segments)) {
            return true;
        }
        if (isAdminAuditEntityPathInvalid(segments)) {
            return true;
        }
        if (isProjectConfigPathInvalid(segments)) {
            return true;
        }
        if (isReportLecturerGroupPathInvalid(segments)) {
            return true;
        }
        return isSyncJobPathInvalid(segments, hasAuthorization);
    }

    private boolean isUserGroupPathInvalid(List<String> segments) {
        if (!"groups".equals(segments.get(1)) || segments.size() < 3) {
            return false;
        }

        if (!isDigits(segments.get(2))) {
            return true;
        }

        if (segments.size() >= 5 && "members".equals(segments.get(3)) && !isDigits(segments.get(4))) {
            return true;
        }

        return false;
    }

    private boolean isUserPathInvalid(List<String> segments) {
        if (!"users".equals(segments.get(1)) || segments.size() < 3) {
            return false;
        }

        String userId = segments.get(2);
        if ("me".equals(userId)) {
            return false;
        }

        return !isDigits(userId);
    }

    private boolean isSemesterPathInvalid(List<String> segments) {
        if (!"semesters".equals(segments.get(1)) || segments.size() < 3) {
            return false;
        }
        if ("active".equals(segments.get(2)) || "code".equals(segments.get(2))) {
            return false;
        }
        return !isDigits(segments.get(2));
    }

    private boolean isAdminUserPathInvalid(List<String> segments) {
        if (segments.size() < 4) {
            return false;
        }
        if (!"admin".equals(segments.get(1)) || !"users".equals(segments.get(2))) {
            return false;
        }
        return !isDigits(segments.get(3));
    }

    private boolean isAdminAuditEntityPathInvalid(List<String> segments) {
        if (segments.size() < 6) {
            return false;
        }
        if (!"admin".equals(segments.get(1))
            || !"audit".equals(segments.get(2))
            || !"entity".equals(segments.get(3))) {
            return false;
        }
        return !isDigits(segments.get(5));
    }

    private boolean isProjectConfigPathInvalid(List<String> segments) {
        if (segments.size() < 4) {
            return false;
        }
        if (!"project-configs".equals(segments.get(1)) || !"group".equals(segments.get(2))) {
            return false;
        }
        return !isDigits(segments.get(3));
    }

    private boolean isReportLecturerGroupPathInvalid(List<String> segments) {
        if (segments.size() < 6) {
            return false;
        }
        if (!"reports".equals(segments.get(1))
            || !"lecturer".equals(segments.get(2))
            || !"groups".equals(segments.get(3))) {
            return false;
        }
        return !isDigits(segments.get(4));
    }

    private boolean isSyncJobPathInvalid(List<String> segments, boolean hasAuthorization) {
        if (segments.size() < 4) {
            return false;
        }
        if (!"sync".equals(segments.get(1)) || !"jobs".equals(segments.get(2))) {
            return false;
        }
        if (!hasAuthorization) {
            return false;
        }
        return !isDigits(segments.get(3));
    }

    private boolean isDigits(String value) {
        return value != null && value.matches(DIGITS_REGEX);
    }
}