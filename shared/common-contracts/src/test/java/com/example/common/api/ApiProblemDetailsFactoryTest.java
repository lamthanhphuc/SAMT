package com.example.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class ApiProblemDetailsFactoryTest {

    @Test
    void createsTypedProblemDetailWithExtension() {
        ProblemDetail problem = ApiProblemDetailsFactory.problemDetail(
                HttpStatus.BAD_REQUEST,
                "invalid-input",
                "Invalid input",
                "Payload is invalid",
                "/api/users",
                details -> details.extension("field", "email")
        );

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getType().toString()).isEqualTo("https://api.example.com/errors/invalid-input");
        assertThat(problem.getTitle()).isEqualTo("Invalid input");
        assertThat(problem.getDetail()).isEqualTo("Payload is invalid");
        assertThat(problem.getInstance().toString()).isEqualTo("/api/users");
        assertThat(problem.getProperties()).containsEntry("field", "email");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void fallsBackToAboutBlankWhenSlugMissing() {
        assertThat(ApiProblemDetailsFactory.type(null).toString()).isEqualTo("about:blank");
        assertThat(ApiProblemDetailsFactory.type(" ").toString()).isEqualTo("about:blank");
    }
}
