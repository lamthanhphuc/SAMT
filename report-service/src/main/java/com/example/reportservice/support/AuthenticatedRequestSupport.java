package com.example.reportservice.support;

import com.example.reportservice.config.CorrelationIdFilter;
import com.example.reportservice.web.BadRequestException;
import com.example.reportservice.web.UpstreamServiceException;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthenticatedRequestSupport {

    public Long requireUserId(Authentication authentication) {
        String subject = authentication != null ? authentication.getName() : null;
        if (subject == null || subject.isBlank()) {
            throw new BadRequestException("Authenticated subject is required");
        }
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Authenticated subject must be a valid user id");
        }
    }

    public List<String> roles(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(authority -> authority.replace("ROLE_", ""))
            .toList();
    }

    public void applyCallerHeaders(HttpHeaders headers) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new UpstreamServiceException("Missing caller JWT for internal service call");
        }

        headers.setBearerAuth(jwtAuth.getToken().getTokenValue());
        String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (requestId != null && !requestId.isBlank()) {
            headers.set(CorrelationIdFilter.HEADER_NAME, requestId);
        }
    }
}