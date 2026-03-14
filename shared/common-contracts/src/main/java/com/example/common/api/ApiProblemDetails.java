package com.example.common.api;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiProblemDetails {

    private URI type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    private Instant timestamp;
    private final Map<String, Object> extensions = new LinkedHashMap<>();

    public ApiProblemDetails() {
    }

    public ApiProblemDetails(URI type, String title, int status, String detail, String instance, Instant timestamp) {
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.timestamp = timestamp;
    }

    public URI getType() {
        return type;
    }

    public void setType(URI type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @JsonAnyGetter
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    @JsonAnySetter
    public void setExtension(String name, Object value) {
        if (value == null) {
            extensions.remove(name);
            return;
        }
        extensions.put(name, value);
    }

    public ApiProblemDetails extension(String name, Object value) {
        setExtension(name, value);
        return this;
    }

    public ProblemDetail toProblemDetail() {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        if (type != null) {
            problemDetail.setType(type);
        }
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        if (instance != null && !instance.isBlank()) {
            problemDetail.setInstance(URI.create(instance));
        }
        if (timestamp != null) {
            problemDetail.setProperty("timestamp", timestamp);
        }
        extensions.forEach(problemDetail::setProperty);
        return problemDetail;
    }

    public static ApiProblemDetails fromProblemDetail(ProblemDetail problemDetail) {
        ApiProblemDetails details = new ApiProblemDetails(
            problemDetail.getType(),
            problemDetail.getTitle(),
            problemDetail.getStatus(),
            problemDetail.getDetail(),
            problemDetail.getInstance() == null ? null : problemDetail.getInstance().toString(),
            null
        );

        Map<String, Object> properties = problemDetail.getProperties();
        if (properties == null || properties.isEmpty()) {
            return details;
        }

        Object timestamp = properties.get("timestamp");
        if (timestamp instanceof Instant instant) {
            details.setTimestamp(instant);
        } else if (timestamp instanceof String value && !value.isBlank()) {
            details.setTimestamp(Instant.parse(value));
        }

        properties.forEach((key, value) -> {
            if (!"timestamp".equals(key)) {
                details.setExtension(key, value);
            }
        });
        return details;
    }
}