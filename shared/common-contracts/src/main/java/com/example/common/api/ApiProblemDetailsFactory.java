package com.example.common.api;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;
import java.util.function.Consumer;

public final class ApiProblemDetailsFactory {

    private static final String ERROR_BASE_URI = "https://api.example.com/errors/";

    private ApiProblemDetailsFactory() {
    }

    public static ApiProblemDetails create(
        HttpStatusCode status,
        String typeSlug,
        String title,
        String detail,
        String instance
    ) {
        return new ApiProblemDetails(
            type(typeSlug),
            title,
            status.value(),
            detail,
            instance,
            Instant.now()
        );
    }

    public static ProblemDetail problemDetail(
        HttpStatusCode status,
        String typeSlug,
        String title,
        String detail,
        String instance
    ) {
        return problemDetail(status, typeSlug, title, detail, instance, null);
    }

    public static ProblemDetail problemDetail(
        HttpStatusCode status,
        String typeSlug,
        String title,
        String detail,
        String instance,
        Consumer<ApiProblemDetails> customizer
    ) {
        ApiProblemDetails details = create(status, typeSlug, title, detail, instance);
        if (customizer != null) {
            customizer.accept(details);
        }
        return details.toProblemDetail();
    }

    public static URI type(String typeSlug) {
        if (typeSlug == null || typeSlug.isBlank()) {
            return URI.create("about:blank");
        }
        return URI.create(ERROR_BASE_URI + typeSlug);
    }
}