package com.example.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared IP resolution service for consistent IP extraction across all gateway components.
 * Used by both rate limiting and audit logging to ensure consistent behavior.
 */
@Service
@Slf4j
public class IpResolutionService {
    
    private final Set<String> trustedProxies;
    
    public IpResolutionService(@Value("${gateway.trusted.proxies:127.0.0.1,::1}") String trustedProxiesConfig) {
        this.trustedProxies = new HashSet<>(Arrays.asList(trustedProxiesConfig.split(",")));
        log.info("Initialized IpResolutionService with trusted proxies: {}", trustedProxies);
    }
    
    /**
     * Resolves client IP address with trusted proxy validation.
     * This method ensures consistent IP resolution across all gateway components.
     * 
     * @param request The HTTP request
     * @return The resolved client IP address
     */
    public String resolveClientIp(ServerHttpRequest request) {
        try {
            String clientIp = resolveClientIpInternal(request);
            if (clientIp == null || !isValidIpAddress(clientIp)) {
                log.warn("Invalid or null IP resolved, using fallback: clientIp={}", clientIp);
                clientIp = getFallbackIp(request);
            }
            return clientIp;
        } catch (Exception e) {
            log.error("Error resolving client IP: {}", e.getMessage());
            return getFallbackIp(request);
        }
    }
    
    /**
     * Internal IP resolution with trusted proxy validation
     */
    private String resolveClientIpInternal(ServerHttpRequest request) {
        String remoteIp = getRemoteAddress(request);
        
        // If request is from trusted proxy, check forwarded headers
        if (isTrustedProxy(remoteIp)) {
            String forwardedIp = getForwardedIp(request);
            if (forwardedIp != null && isValidIpAddress(forwardedIp)) {
                log.debug("Using X-Forwarded-For IP from trusted proxy: {} -> {}", remoteIp, forwardedIp);
                return forwardedIp;
            }
        } else {
            // Check if untrusted client is trying to spoof headers
            String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                log.warn("Untrusted client attempting X-Forwarded-For spoofing: clientIp={} spoofedHeader={}", 
                        remoteIp, xForwardedFor);
            }
        }
        
        return remoteIp;
    }
    
    /**
     * Get X-Forwarded-For header safely
     */
    private String getForwardedIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP in the chain (original client)
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }
        
        return null;
    }
    
    /**
     * Get remote address safely
     */
    private String getRemoteAddress(ServerHttpRequest request) {
        return request.getRemoteAddress() != null 
            ? request.getRemoteAddress().getAddress().getHostAddress() 
            : null;
    }
    
    /**
     * Check if IP is from trusted proxy
     */
    private boolean isTrustedProxy(String ip) {
        return ip != null && trustedProxies.contains(ip);
    }
    
    /**
     * Validate IP address format
     */
    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }
        
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
    
    /**
     * Fallback IP when resolution fails
     */
    private String getFallbackIp(ServerHttpRequest request) {
        String remoteIp = getRemoteAddress(request);
        if (remoteIp != null && isValidIpAddress(remoteIp)) {
            return remoteIp;
        }
        
        // Ultimate fallback for rate limiting
        return "unknown";
    }
}