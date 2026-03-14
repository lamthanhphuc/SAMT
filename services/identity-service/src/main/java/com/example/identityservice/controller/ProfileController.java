package com.example.identityservice.controller;

import com.example.common.api.ApiResponseFactory;
import com.example.identityservice.dto.UpdateProfileRequest;
import com.example.identityservice.dto.UserDto;
import com.example.identityservice.service.UserService;
import com.example.identityservice.web.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Current-user profile endpoints.
 */
@RestController
@RequestMapping("/profile")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get current user profile")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile retrieved",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
    })
    @GetMapping
    public ResponseEntity<com.example.common.api.ApiResponse<UserDto>> getCurrentProfile(HttpServletRequest servletRequest) {
        Long userId = getAuthenticatedUserId();
        UserDto userDto = UserDto.fromEntity(userService.getCurrentUserProfile(userId));
        return success(HttpStatus.OK, userDto, servletRequest);
    }

    @Operation(summary = "Update current user profile")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile updated",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "Email already exists",
            content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
    })
    @PutMapping
    public ResponseEntity<com.example.common.api.ApiResponse<UserDto>> updateCurrentProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest servletRequest) {
        Long userId = getAuthenticatedUserId();
        UserDto userDto = UserDto.fromEntity(userService.updateCurrentUserProfile(userId, request));
        return success(HttpStatus.OK, userDto, servletRequest);
    }

    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof com.example.identityservice.entity.User user)) {
            throw new IllegalArgumentException("Authenticated user not found in security context");
        }
        return user.getId();
    }

    private <T> ResponseEntity<com.example.common.api.ApiResponse<T>> success(HttpStatus status, T data, HttpServletRequest request) {
        return ResponseEntity.status(status).body(
            ApiResponseFactory.success(
                status.value(),
                data,
                request.getRequestURI(),
                resolveCorrelationId(request)
            )
        );
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        }
        return correlationId;
    }
}