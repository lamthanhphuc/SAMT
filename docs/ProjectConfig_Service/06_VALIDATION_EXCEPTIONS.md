# VALIDATION & EXCEPTIONS – PROJECT CONFIG SERVICE

## 1. Field-Level Validation Rules

### 1.1 Group ID Validation

**Field:** `groupId`  
**Type:** UUID  
**Constraints:**

| Rule | Validation |
|------|------------|
| Required | `@NotNull` |
| Format | Must be valid UUID |
| Existence | Group must exist in database |

**Validation Messages:**

```java
@NotNull(message = "Group ID is required")
private UUID groupId;
```

**Custom Validator:**

```java
@Validated
public class CreateConfigRequest {
    
    @NotNull(message = "Group ID is required")
    @GroupExists // Custom annotation
    private UUID groupId;
}

// Custom validator implementation
@Constraint(validatedBy = GroupExistsValidator.class)
public @interface GroupExists {
    String message() default "Group does not exist";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class GroupExistsValidator implements ConstraintValidator<GroupExists, UUID> {
    
    @Autowired
    private GroupRepository groupRepository;
    
    @Override
    public boolean isValid(UUID groupId, ConstraintValidatorContext context) {
        if (groupId == null) return true; // @NotNull will handle null
        return groupRepository.existsById(groupId);
    }
}
```

---

### 1.2 Jira Host URL Validation

**Field:** `jiraHostUrl`  
**Type:** String  
**Constraints:**

| Rule | Validation |
|------|------------|
| Required | `@NotBlank` |
| Max Length | 255 characters |
| Format | Must be `https://*.atlassian.net` |
| Protocol | HTTPS only |

**Validation Annotations:**

```java
@NotBlank(message = "Jira host URL is required")
@Size(max = 255, message = "Jira host URL must not exceed 255 characters")
@Pattern(
    regexp = "^https://[a-zA-Z0-9-]+\\.atlassian\\.net$",
    message = "Jira host URL must be a valid Atlassian URL (https://*.atlassian.net)"
)
private String jiraHostUrl;
```

**Error Examples:**

```json
{
  "field": "jiraHostUrl",
  "message": "Jira host URL must be a valid Atlassian URL (https://*.atlassian.net)",
  "providedValue": "http://project.atlassian.net",
  "reason": "HTTP protocol not allowed"
}
```

---

### 1.3 Jira API Token Validation

**Field:** `jiraApiToken`  
**Type:** String (Encrypted)  
**Constraints:**

| Rule | Validation |
|------|------------|
| Required | `@NotBlank` |
| Format | Must start with `ATATT` |
| Min Length | 20 characters |
| Max Length | 500 characters (encrypted) |

**Validation Annotations:**

```java
@NotBlank(message = "Jira API token is required")
@Size(min = 20, max = 500, message = "Jira API token length must be between 20 and 500 characters")
@Pattern(
    regexp = "^ATATT[a-zA-Z0-9]+$",
    message = "Jira API token must start with 'ATATT' and contain only alphanumeric characters"
)
private String jiraApiToken;
```

**Error Examples:**

```json
{
  "field": "jiraApiToken",
  "message": "Jira API token must start with 'ATATT'",
  "providedValue": "invalid_token_format"
}
```

---

### 1.4 GitHub Repository URL Validation

**Field:** `githubRepoUrl`  
**Type:** String  
**Constraints:**

| Rule | Validation |
|------|------------|
| Required | `@NotBlank` |
| Max Length | 255 characters |
| Format | `https://github.com/{owner}/{repo}` |
| Protocol | HTTPS only |

**Validation Annotations:**

```java
@NotBlank(message = "GitHub repository URL is required")
@Size(max = 255, message = "GitHub repository URL must not exceed 255 characters")
@Pattern(
    regexp = "^https://github\\.com/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+$",
    message = "GitHub repository URL must be a valid format (https://github.com/owner/repo)"
)
private String githubRepoUrl;
```

**Error Examples:**

```json
{
  "field": "githubRepoUrl",
  "message": "GitHub repository URL must be a valid format (https://github.com/owner/repo)",
  "providedValue": "https://github.com/owner/repo.git",
  "reason": ".git suffix not allowed"
}
```

---

### 1.5 GitHub Token Validation

**Field:** `githubToken`  
**Type:** String (Encrypted)  
**Constraints:**

| Rule | Validation |
|------|------------|
| Required | `@NotBlank` |
| Format | Must start with `ghp_` |
| Length | 44 characters (`ghp_` + 40 chars) |

**Validation Annotations:**

```java
@NotBlank(message = "GitHub token is required")
@Pattern(
    regexp = "^ghp_[a-zA-Z0-9]{40}$",
    message = "GitHub token must start with 'ghp_' followed by 40 alphanumeric characters"
)
private String githubToken;
```

**Error Examples:**

```json
{
  "field": "githubToken",
  "message": "GitHub token must start with 'ghp_' followed by 40 alphanumeric characters",
  "providedValue": "ghp_short"
}
```

---

## 2. DTO Classes with Validation

### 2.1 CreateConfigRequest

```java
package com.samt.projectconfig.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConfigRequest {
    
    @NotNull(message = "Group ID is required")
    @GroupExists
    private UUID groupId;
    
    @NotBlank(message = "Jira host URL is required")
    @Size(max = 255, message = "Jira host URL must not exceed 255 characters")
    @Pattern(
        regexp = "^https://[a-zA-Z0-9-]+\\.atlassian\\.net$",
        message = "Jira host URL must be a valid Atlassian URL (https://*.atlassian.net)"
    )
    private String jiraHostUrl;
    
    @NotBlank(message = "Jira API token is required")
    @Size(min = 20, max = 500, message = "Jira API token length must be between 20 and 500")
    @Pattern(
        regexp = "^ATATT[a-zA-Z0-9]+$",
        message = "Jira API token must start with 'ATATT'"
    )
    private String jiraApiToken;
    
    @NotBlank(message = "GitHub repository URL is required")
    @Size(max = 255, message = "GitHub repository URL must not exceed 255 characters")
    @Pattern(
        regexp = "^https://github\\.com/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+$",
        message = "GitHub repository URL must be a valid format (https://github.com/owner/repo)"
    )
    private String githubRepoUrl;
    
    @NotBlank(message = "GitHub token is required")
    @Pattern(
        regexp = "^ghp_[a-zA-Z0-9]{40}$",
        message = "GitHub token must start with 'ghp_' followed by 40 characters"
    )
    private String githubToken;
}
```

---

### 2.2 UpdateConfigRequest

```java
package com.samt.projectconfig.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConfigRequest {
    
    // All fields optional for partial updates
    
    @Size(max = 255, message = "Jira host URL must not exceed 255 characters")
    @Pattern(
        regexp = "^https://[a-zA-Z0-9-]+\\.atlassian\\.net$",
        message = "Jira host URL must be a valid Atlassian URL"
    )
    private String jiraHostUrl;
    
    @Size(min = 20, max = 500, message = "Jira API token length must be between 20 and 500")
    @Pattern(
        regexp = "^ATATT[a-zA-Z0-9]+$",
        message = "Jira API token must start with 'ATATT'"
    )
    private String jiraApiToken;
    
    @Size(max = 255, message = "GitHub repository URL must not exceed 255 characters")
    @Pattern(
        regexp = "^https://github\\.com/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+$",
        message = "GitHub repository URL must be a valid format"
    )
    private String githubRepoUrl;
    
    @Pattern(
        regexp = "^ghp_[a-zA-Z0-9]{40}$",
        message = "GitHub token must start with 'ghp_' followed by 40 characters"
    )
    private String githubToken;
}
```

---

### 2.3 VerifyCredentialsRequest

```java
package com.samt.projectconfig.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyCredentialsRequest {
    
    @NotBlank(message = "Jira host URL is required")
    @Pattern(
        regexp = "^https://[a-zA-Z0-9-]+\\.atlassian\\.net$",
        message = "Jira host URL must be a valid Atlassian URL"
    )
    private String jiraHostUrl;
    
    @NotBlank(message = "Jira API token is required")
    @Pattern(
        regexp = "^ATATT[a-zA-Z0-9]+$",
        message = "Jira API token must start with 'ATATT'"
    )
    private String jiraApiToken;
    
    @NotBlank(message = "GitHub repository URL is required")
    @Pattern(
        regexp = "^https://github\\.com/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+$",
        message = "GitHub repository URL must be a valid format"
    )
    private String githubRepoUrl;
    
    @NotBlank(message = "GitHub token is required")
    @Pattern(
        regexp = "^ghp_[a-zA-Z0-9]{40}$",
        message = "GitHub token must start with 'ghp_'"
    )
    private String githubToken;
}
```

---

## 3. Exception Hierarchy

### 3.1 Base Exception

```java
package com.samt.projectconfig.exception;

import lombok.Getter;

@Getter
public abstract class ProjectConfigException extends RuntimeException {
    
    private final String code;
    
    public ProjectConfigException(String code, String message) {
        super(message);
        this.code = code;
    }
    
    public ProjectConfigException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
```

---

### 3.2 Specific Exceptions

#### Config Not Found Exception

```java
package com.samt.projectconfig.exception;

import java.util.UUID;

public class ConfigNotFoundException extends ProjectConfigException {
    
    public ConfigNotFoundException(UUID configId) {
        super(
            "CONFIG_NOT_FOUND",
            String.format("Project config with ID '%s' not found", configId)
        );
    }
    
    public ConfigNotFoundException(String message) {
        super("CONFIG_NOT_FOUND", message);
    }
}
```

---

#### Config Already Exists Exception

```java
package com.samt.projectconfig.exception;

import java.util.UUID;

public class ConfigAlreadyExistsException extends ProjectConfigException {
    
    public ConfigAlreadyExistsException(UUID groupId) {
        super(
            "CONFIG_ALREADY_EXISTS",
            String.format("Project config for group '%s' already exists", groupId)
        );
    }
}
```

---

#### Invalid URL Format Exception

```java
package com.samt.projectconfig.exception;

public class InvalidUrlFormatException extends ProjectConfigException {
    
    public InvalidUrlFormatException(String field, String url) {
        super(
            "INVALID_URL_FORMAT",
            String.format("Invalid URL format for field '%s': %s", field, url)
        );
    }
}
```

---

#### Invalid Token Format Exception

```java
package com.samt.projectconfig.exception;

public class InvalidTokenFormatException extends ProjectConfigException {
    
    public InvalidTokenFormatException(String field, String reason) {
        super(
            "INVALID_TOKEN_FORMAT",
            String.format("Invalid token format for field '%s': %s", field, reason)
        );
    }
}
```

---

#### Connection Failed Exception

```java
package com.samt.projectconfig.exception;

public class ConnectionFailedException extends ProjectConfigException {
    
    public ConnectionFailedException(String service, String reason) {
        super(
            "CONNECTION_FAILED",
            String.format("Failed to connect to %s: %s", service, reason)
        );
    }
    
    public ConnectionFailedException(String service, Throwable cause) {
        super(
            "CONNECTION_FAILED",
            String.format("Failed to connect to %s", service),
            cause
        );
    }
}
```

---

#### Invalid Credentials Exception

```java
package com.samt.projectconfig.exception;

public class InvalidCredentialsException extends ProjectConfigException {
    
    public InvalidCredentialsException(String service) {
        super(
            "INVALID_CREDENTIALS",
            String.format("Authentication failed for %s: Invalid credentials or insufficient permissions", service)
        );
    }
}
```

---

#### Encryption Error Exception

```java
package com.samt.projectconfig.exception;

public class EncryptionException extends ProjectConfigException {
    
    public EncryptionException(String message) {
        super("ENCRYPTION_ERROR", message);
    }
    
    public EncryptionException(String message, Throwable cause) {
        super("ENCRYPTION_ERROR", message, cause);
    }
}
```

---

#### Group Not Found Exception

```java
package com.samt.projectconfig.exception;

import java.util.UUID;

public class GroupNotFoundException extends ProjectConfigException {
    
    public GroupNotFoundException(UUID groupId) {
        super(
            "GROUP_NOT_FOUND",
            String.format("Group with ID '%s' not found", groupId)
        );
    }
}
```

---

#### Forbidden Exception

```java
package com.samt.projectconfig.exception;

public class ForbiddenException extends ProjectConfigException {
    
    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}
```

---

#### Rate Limit Exceeded Exception

```java
package com.samt.projectconfig.exception;

public class RateLimitExceededException extends ProjectConfigException {
    
    public RateLimitExceededException(String message) {
        super("RATE_LIMIT_EXCEEDED", message);
    }
    
    public RateLimitExceededException(int retryAfterSeconds) {
        super(
            "RATE_LIMIT_EXCEEDED",
            String.format("Rate limit exceeded. Please try again after %d seconds.", retryAfterSeconds)
        );
    }
}
```

---

#### Concurrent Update Exception

```java
package com.samt.projectconfig.exception;

public class ConcurrentUpdateException extends ProjectConfigException {
    
    public ConcurrentUpdateException() {
        super(
            "CONCURRENT_UPDATE",
            "Config was updated by another user. Please refresh and try again."
        );
    }
}
```

---

#### Config Expired Exception

```java
package com.samt.projectconfig.exception;

public class ConfigExpiredException extends ProjectConfigException {
    
    public ConfigExpiredException(String message) {
        super("CONFIG_EXPIRED", message);
    }
}
```

---

## 4. Global Exception Handler

```java
package com.samt.projectconfig.exception;

import lombok.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.FieldError;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    // 1. Validation Error Handler
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationErrors(
        MethodArgumentNotValidException ex
    ) {
        List<FieldErrorDetail> errors = ex.getBindingResult()
            .getAllErrors()
            .stream()
            .map(error -> {
                String field = ((FieldError) error).getField();
                String message = error.getDefaultMessage();
                Object rejectedValue = ((FieldError) error).getRejectedValue();
                
                return FieldErrorDetail.builder()
                    .field(field)
                    .message(message)
                    .rejectedValue(rejectedValue)
                    .build();
            })
            .collect(Collectors.toList());
        
        ValidationErrorResponse response = ValidationErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .message("Validation failed")
            .timestamp(LocalDateTime.now())
            .errors(errors)
            .build();
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }
    
    // 2. Config Not Found
    @ExceptionHandler(ConfigNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleConfigNotFound(
        ConfigNotFoundException ex
    ) {
        return buildErrorResponse(ex, HttpStatus.NOT_FOUND);
    }
    
    // 3. Config Already Exists
    @ExceptionHandler(ConfigAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleConfigAlreadyExists(
        ConfigAlreadyExistsException ex
    ) {
        return buildErrorResponse(ex, HttpStatus.CONFLICT);
    }
    
    // 4. Invalid URL/Token Format
    @ExceptionHandler({InvalidUrlFormatException.class, InvalidTokenFormatException.class})
    public ResponseEntity<ErrorResponse> handleInvalidFormat(
        ProjectConfigException ex
    ) {
        return buildErrorResponse(ex, HttpStatus.BAD_REQUEST);
    }
    
    // 5. Connection Failed
    @ExceptionHandler(ConnectionFailedException.class)
    public ResponseEntity<ErrorResponse> handleConnectionFailed(
        ConnectionFailedException ex
    ) {
        return buildErrorResponse(ex, HttpStatus.UNPROCESSABLE_ENTITY);
    }
    
    // 6. Invalid Credentials
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
        InvalidCredentialsException ex
    ) {
        return buildErrorResponse(ex, HttpStatus.UNPROCESSABLE_ENTITY);
    }
    
    // 7. Forbidden
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
        ForbiddenException ex
    ) {
        return buildErrorResponse(ex, HttpStatus.FORBIDDEN);
    }
    
    // 8. Group Not Found
    @ExceptionHandler(GroupNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGroupNotFound(
        GroupNotFoundException ex
    ) {
        return buildErrorResponse(ex, HttpStatus.NOT_FOUND);
    }
    
    // 9. Rate Limit Exceeded
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
        RateLimitExceededException ex
    ) {
        ErrorResponse response = ErrorResponse.builder()
            .code(ex.getCode())
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", "60")
            .body(response);
    }
    
    // 10. Concurrent Update
    @ExceptionHandler(ConcurrentUpdateException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentUpdate(
        ConcurrentUpdateException ex
    ) {
        return buildErrorResponse(ex, HttpStatus.CONFLICT);
    }
    
    // 11. Encryption Error
    @ExceptionHandler(EncryptionException.class)
    public ResponseEntity<ErrorResponse> handleEncryptionError(
        EncryptionException ex
    ) {
        return buildErrorResponse(ex, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    // 12. Generic Exception
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
        Exception ex
    ) {
        ErrorResponse response = ErrorResponse.builder()
            .code("INTERNAL_SERVER_ERROR")
            .message("An unexpected error occurred")
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
    }
    
    // Helper method
    private ResponseEntity<ErrorResponse> buildErrorResponse(
        ProjectConfigException ex,
        HttpStatus status
    ) {
        ErrorResponse response = ErrorResponse.builder()
            .code(ex.getCode())
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(status).body(response);
    }
}
```

---

## 5. Error Response DTOs

### 5.1 ErrorResponse

```java
package com.samt.projectconfig.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    private String code;
    private String message;
    private LocalDateTime timestamp;
}
```

---

### 5.2 ValidationErrorResponse

```java
package com.samt.projectconfig.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationErrorResponse {
    
    private String code;
    private String message;
    private LocalDateTime timestamp;
    private List<FieldErrorDetail> errors;
}
```

---

### 5.3 FieldErrorDetail

```java
package com.samt.projectconfig.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldErrorDetail {
    
    private String field;
    private String message;
    private Object rejectedValue;
}
```

---

## 6. Validation Error Examples

### Example 1: Missing Required Fields

**Request:**
```json
{
  "groupId": null,
  "jiraHostUrl": "",
  "githubRepoUrl": "https://github.com/owner/repo"
}
```

**Response:** `400 BAD_REQUEST`
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "timestamp": "2026-01-29T10:00:00Z",
  "errors": [
    {
      "field": "groupId",
      "message": "Group ID is required",
      "rejectedValue": null
    },
    {
      "field": "jiraHostUrl",
      "message": "Jira host URL is required",
      "rejectedValue": ""
    },
    {
      "field": "jiraApiToken",
      "message": "Jira API token is required",
      "rejectedValue": null
    },
    {
      "field": "githubToken",
      "message": "GitHub token is required",
      "rejectedValue": null
    }
  ]
}
```

---

### Example 2: Invalid URL Format

**Request:**
```json
{
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraHostUrl": "http://project.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0T1234567890",
  "githubRepoUrl": "https://gitlab.com/owner/repo",
  "githubToken": "ghp_abc123xyz456def789ghi012jkl345mno678"
}
```

**Response:** `400 BAD_REQUEST`
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "timestamp": "2026-01-29T10:00:00Z",
  "errors": [
    {
      "field": "jiraHostUrl",
      "message": "Jira host URL must be a valid Atlassian URL (https://*.atlassian.net)",
      "rejectedValue": "http://project.atlassian.net"
    },
    {
      "field": "githubRepoUrl",
      "message": "GitHub repository URL must be a valid format (https://github.com/owner/repo)",
      "rejectedValue": "https://gitlab.com/owner/repo"
    }
  ]
}
```

---

### Example 3: Invalid Token Format

**Request:**
```json
{
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraHostUrl": "https://project.atlassian.net",
  "jiraApiToken": "invalid_token",
  "githubRepoUrl": "https://github.com/owner/repo",
  "githubToken": "ghp_short"
}
```

**Response:** `400 BAD_REQUEST`
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "timestamp": "2026-01-29T10:00:00Z",
  "errors": [
    {
      "field": "jiraApiToken",
      "message": "Jira API token must start with 'ATATT'",
      "rejectedValue": "invalid_token"
    },
    {
      "field": "githubToken",
      "message": "GitHub token must start with 'ghp_' followed by 40 characters",
      "rejectedValue": "ghp_short"
    }
  ]
}
```

---

## 7. Exception ↔ HTTP Status Mapping

| Exception | HTTP Status | Error Code |
|-----------|-------------|------------|
| ConfigNotFoundException | 404 | CONFIG_NOT_FOUND |
| ConfigAlreadyExistsException | 409 | CONFIG_ALREADY_EXISTS |
| InvalidUrlFormatException | 400 | INVALID_URL_FORMAT |
| InvalidTokenFormatException | 400 | INVALID_TOKEN_FORMAT |
| ConnectionFailedException | 422 | CONNECTION_FAILED |
| InvalidCredentialsException | 422 | INVALID_CREDENTIALS |
| ForbiddenException | 403 | FORBIDDEN |
| GroupNotFoundException | 404 | GROUP_NOT_FOUND |
| RateLimitExceededException | 429 | RATE_LIMIT_EXCEEDED |
| ConcurrentUpdateException | 409 | CONCURRENT_UPDATE |
| EncryptionException | 500 | ENCRYPTION_ERROR |
| MethodArgumentNotValidException | 400 | VALIDATION_ERROR |
| Generic Exception | 500 | INTERNAL_SERVER_ERROR |

---

## 8. Controller Validation Example

```java
package com.samt.projectconfig.controller;

import com.samt.projectconfig.dto.request.*;
import com.samt.projectconfig.dto.response.*;
import com.samt.projectconfig.service.ProjectConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/project-configs")
@RequiredArgsConstructor
@Validated
public class ProjectConfigController {
    
    private final ProjectConfigService service;
    
    @PostMapping
    public ResponseEntity<ProjectConfigResponse> createConfig(
        @Valid @RequestBody CreateConfigRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        ProjectConfigResponse response = service.createConfig(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PutMapping("/{configId}")
    public ResponseEntity<ProjectConfigResponse> updateConfig(
        @PathVariable UUID configId,
        @Valid @RequestBody UpdateConfigRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        ProjectConfigResponse response = service.updateConfig(configId, request, userId);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/verify")
    public ResponseEntity<VerificationResult> verifyCredentials(
        @Valid @RequestBody VerifyCredentialsRequest request
    ) {
        VerificationResult result = service.verifyCredentials(request);
        return ResponseEntity.ok(result);
    }
}
```

---

## Summary

### Validation Coverage

| Category | Rules Count |
|----------|-------------|
| Field Validation | 5 (groupId, jiraHostUrl, jiraApiToken, githubRepoUrl, githubToken) |
| Custom Validators | 1 (@GroupExists) |
| Exception Types | 11 specific exceptions |
| HTTP Status Codes | 6 (400, 401, 403, 404, 409, 422, 429, 500) |

### Key Points

1. **Jakarta Validation:** Sử dụng `@Valid`, `@NotNull`, `@NotBlank`, `@Pattern`, `@Size`
2. **Custom Validators:** Tạo `@GroupExists` để validate group existence
3. **Exception Hierarchy:** Base class `ProjectConfigException` với specific exceptions
4. **Global Handler:** Centralized error handling với `@RestControllerAdvice`
5. **Consistent Responses:** Standardized error response format cho tất cả endpoints
6. **Field-Level Errors:** Detailed validation errors với field name, message, và rejected value
