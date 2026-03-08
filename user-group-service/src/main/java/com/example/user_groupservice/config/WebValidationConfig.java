package com.example.user_groupservice.config;

import com.example.user_groupservice.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Configuration
public class WebValidationConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new StrictQueryParameterInterceptor());
    }

    private static final class StrictQueryParameterInterceptor implements HandlerInterceptor {

        private static final List<QueryParameterRule> QUERY_PARAMETER_RULES = List.of(
                new QueryParameterRule("GET", Pattern.compile("^/api/groups/?$"), Set.of("page", "size", "semesterId", "lecturerId")),
                new QueryParameterRule("GET", Pattern.compile("^/api/users/?$"), Set.of("page", "size", "status", "role")),
                new QueryParameterRule("GET", Pattern.compile("^/api/users/[^/]+/groups/?$"), Set.of("semester", "semesterId"))
        );

        @Override
        public boolean preHandle(HttpServletRequest request,
                                 jakarta.servlet.http.HttpServletResponse response,
                                 Object handler) {
            QueryParameterRule rule = QUERY_PARAMETER_RULES.stream()
                    .filter(candidate -> candidate.matches(request))
                    .findFirst()
                    .orElse(null);

            if (rule == null) {
                return true;
            }

            for (String parameterName : request.getParameterMap().keySet()) {
                if (!rule.allowedParams().contains(parameterName)) {
                    throw new BadRequestException(
                            "UNKNOWN_QUERY_PARAMETER",
                            "Unknown query parameter '" + parameterName + "'"
                    );
                }
            }

            return true;
        }

        private record QueryParameterRule(String method, Pattern pathPattern, Set<String> allowedParams) {
            private boolean matches(HttpServletRequest request) {
                return method.equals(request.getMethod())
                        && pathPattern.matcher(request.getRequestURI()).matches();
            }
        }
    }
}