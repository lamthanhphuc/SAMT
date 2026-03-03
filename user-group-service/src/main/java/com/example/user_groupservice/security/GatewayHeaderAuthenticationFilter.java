package com.example.user_groupservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Downstream trust model:
 * - API Gateway validates external JWT (RS256 via JWKS)
 * - Downstream services authenticate ONLY via signed gateway headers
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    private final GatewayInternalSignatureVerifier signatureVerifier;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String userIdHeader = request.getHeader("X-User-Id");
        String roleHeader = request.getHeader("X-User-Role");

        // If headers are not present, leave unauthenticated; SecurityConfig will enforce auth.
        if (!StringUtils.hasText(userIdHeader) || !StringUtils.hasText(roleHeader)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!signatureVerifier.verify(request)) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + roleHeader));
        CurrentUser currentUser = new CurrentUser(userId, authorities);

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                currentUser,
                null,
                authorities
        );
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        log.debug("Gateway header authentication successful: userId={}, role={}", userId, roleHeader);
        filterChain.doFilter(request, response);
    }
}
