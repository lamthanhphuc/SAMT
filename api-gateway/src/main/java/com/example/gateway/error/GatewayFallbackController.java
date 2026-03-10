package com.example.gateway.error;

import com.example.common.api.ApiResponse;
import com.example.common.api.ApiResponseFactory;
import com.example.gateway.filter.CorrelationIdWebFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;
import java.util.Set;

@RestController
public class GatewayFallbackController {

    @RequestMapping("/__gateway/fallback/{service}")
    public Mono<ResponseEntity<ApiResponse<Void>>> fallback(@PathVariable("service") String service, org.springframework.web.server.ServerWebExchange exchange) {
        String correlationId = GatewayErrorResponseWriter.resolveCorrelationId(
            exchange.getRequest().getHeaders().getFirst(GatewayErrorResponseWriter.HEADER_NAME)
        );
        exchange.getResponse().getHeaders().set(GatewayErrorResponseWriter.HEADER_NAME, correlationId);

        ApiResponse<Void> body = ApiResponseFactory.error(
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
            humanMessage(service),
            resolveOriginalPath(exchange),
            correlationId
        );
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }

    private String resolveOriginalPath(org.springframework.web.server.ServerWebExchange exchange) {
        String preservedPath = exchange.getAttribute(CorrelationIdWebFilter.ORIGINAL_REQUEST_PATH_ATTRIBUTE);
        if (preservedPath != null && !preservedPath.isBlank() && !preservedPath.startsWith("/__gateway/fallback/")) {
            return preservedPath;
        }

        Set<URI> originalUris = exchange.getAttributeOrDefault(
            ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR,
            Collections.emptySet()
        );
        return originalUris.stream()
            .map(URI::getPath)
            .filter(path -> path != null && !path.isBlank())
            .filter(path -> !path.startsWith("/__gateway/fallback/"))
            .findFirst()
            .or(() -> originalUris.stream()
            .map(URI::getPath)
            .filter(path -> path != null && !path.isBlank())
            .findFirst())
            .orElse(exchange.getRequest().getPath().value());
    }

    private String humanMessage(String service) {
        return switch (service) {
            case "identity" -> "Identity service is temporarily unavailable";
            case "user-group" -> "User group service is temporarily unavailable";
            case "project-config" -> "Project config service is temporarily unavailable";
            case "sync" -> "Sync service is temporarily unavailable";
            case "analysis" -> "Analysis service is temporarily unavailable";
            case "report" -> "Report service is temporarily unavailable";
            case "notification" -> "Notification service is temporarily unavailable";
            default -> "Requested service is temporarily unavailable";
        };
    }
}