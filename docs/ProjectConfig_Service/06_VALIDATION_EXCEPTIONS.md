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
| **Error Codes** | **28 standardized codes (PC-4xx, PC-5xx)** |

### Key Points

1. **Jakarta Validation:** Sử dụng `@Valid`, `@NotNull`, `@NotBlank`, `@Pattern`, `@Size`
2. **Custom Validators:** Tạo `@GroupExists` để validate group existence
3. **Exception Hierarchy:** Base class `ProjectConfigException` với specific exceptions
4. **Global Handler:** Centralized error handling với `@RestControllerAdvice`
5. **Consistent Responses:** Standardized error response format cho tất cả endpoints
6. **Field-Level Errors:** Detailed validation errors với field name, message, và rejected value
7. **Error Code Standards:** Prefix PC-4xx (client errors), PC-5xx (server errors)

---

## 6. Standardized Error Codes

### 6.1 Error Code Format

**Format:** `PC-<HTTP_STATUS>-<SEQUENCE>`

**Components:**
- **PC:** Project Config Service prefix
- **HTTP_STATUS:** 3-digit HTTP status code
- **SEQUENCE:** 2-digit sequence number (01-99)

**Examples:**
- `PC-400-01` → Bad Request - Validation Error
- `PC-403-01` → Forbidden - User is not leader
- `PC-409-01` → Conflict - Config already exists

---

### 6.2 Client Error Codes (4xx)

#### 400 Bad Request

| Error Code | Message | Description | HTTP Status |
|------------|---------|-------------|-------------|
| `PC-400-01` | Validation failed | Generic validation error (see errors array) | 400 |
| `PC-400-02` | Invalid Jira URL format | Jira URL doesn't match pattern | 400 |
| `PC-400-03` | Invalid GitHub URL format | GitHub URL doesn't match pattern | 400 |
| `PC-400-04` | Invalid Jira token format | Token doesn't start with ATATT | 400 |
| `PC-400-05` | Invalid GitHub token format | Token doesn't start with ghp_ | 400 |
| `PC-400-06` | Config is deleted | Cannot update deleted config | 400 |
| `PC-400-07` | Config not deleted | Cannot restore non-deleted config | 400 |

**Example Response:**
```json
{
  "error": "PC-400-01",
  "message": "Validation failed",
  "timestamp": "2026-01-29T15:00:00Z",
  "path": "/api/project-configs",
  "errors": [
    {
      "field": "jiraHostUrl",
      "message": "Invalid Jira URL format",
      "rejectedValue": "http://project.atlassian.net"
    }
  ]
}
```

---

#### 401 Unauthorized

| Error Code | Message | Description | HTTP Status |
|------------|---------|-------------|-------------|
| `PC-401-01` | Missing service authentication headers | X-Service-Name or X-Service-Key missing | 401 |
| `PC-401-02` | Invalid service key | Service key doesn't match expected value | 401 |
| `PC-401-03` | Unknown service name | Service name not in whitelist | 401 |
| `PC-401-04` | JWT token expired | Access token expired | 401 |
| `PC-401-05` | Invalid JWT token | Token signature invalid | 401 |

**Example Response:**
```json
{
  "error": "PC-401-01",
  "message": "Missing service authentication headers",
  "timestamp": "2026-01-29T15:00:00Z",
  "path": "/internal/project-configs/7c9e6679-7425-40de-944b-e07fc1f90ae7/tokens",
  "requiredHeaders": ["X-Service-Name", "X-Service-Key"]
}
```

---

#### 403 Forbidden

| Error Code | Message | Description | HTTP Status |
|------------|---------|-------------|-------------|
| `PC-403-01` | User is not leader of group | Team leader authorization failed | 403 |
| `PC-403-02` | Service does not have READ_DECRYPTED_TOKENS permission | Service lacks required permission | 403 |
| `PC-403-03` | Lecturer not supervising group | Lecturer cannot access config of non-supervised group | 403 |
| `PC-403-04` | Insufficient role | User role insufficient for action | 403 |

**Example Response:**
```json
{
  "error": "PC-403-01",
  "message": "User is not leader of group",
  "timestamp": "2026-01-29T15:00:00Z",
  "path": "/api/project-configs",
  "details": {
    "userId": "e1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
    "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "userRole": "STUDENT"
  }
}
```

---

#### 404 Not Found

| Error Code | Message | Description | HTTP Status |
|------------|---------|-------------|-------------|
| `PC-404-01` | Config not found | Config with given ID doesn't exist | 404 |
| `PC-404-02` | Config not found for group | Group doesn't have a config | 404 |
| `PC-404-03` | Group not found | Group with given ID doesn't exist | 404 |

**Example Response:**
```json
{
  "error": "PC-404-01",
  "message": "Config not found",
  "timestamp": "2026-01-29T15:00:00Z",
  "path": "/api/project-configs/99999999-9999-9999-9999-999999999999",
  "configId": "99999999-9999-9999-9999-999999999999"
}
```

---

#### 409 Conflict

| Error Code | Message | Description | HTTP Status |
|------------|---------|-------------|-------------|
| `PC-409-01` | Config already exists for group | Group already has a config (UNIQUE constraint) | 409 |
| `PC-409-02` | Concurrent modification detected | Optimistic lock version mismatch | 409 |

**Example Response:**
```json
{
  "error": "PC-409-01",
  "message": "Config already exists for group",
  "timestamp": "2026-01-29T15:00:00Z",
  "path": "/api/project-configs",
  "details": {
    "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "existingConfigId": "7c9e6679-7425-40de-944b-e07fc1f90ae7"
  }
}
```

---

#### 422 Unprocessable Entity

| Error Code | Message | Description | HTTP Status |
|------------|---------|-------------|-------------|
| `PC-422-01` | Token encryption failed | Cannot encrypt token (encryption service error) | 422 |
| `PC-422-02` | Token decryption failed | Cannot decrypt token (key mismatch or corrupted data) | 422 |
| `PC-422-03` | Invalid state transition | State transition not allowed by state machine | 422 |
| `PC-422-04` | Verification timeout | Connection verification timed out | 422 |

**Example Response:**
```json
{
  "error": "PC-422-02",
  "message": "Token decryption failed",
  "timestamp": "2026-01-29T15:00:00Z",
  "path": "/api/project-configs/7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "details": {
    "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "reason": "Encryption key mismatch or data corrupted"
  }
}
```

---

#### 429 Too Many Requests

| Error Code | Message | Description | HTTP Status |
|------------|---------|-------------|-------------|
| `PC-429-01` | Rate limit exceeded for verification | Too many verify requests from user | 429 |
| `PC-429-02` | Rate limit exceeded for API | General API rate limit exceeded | 429 |

**Example Response:**
```json
{
  "error": "PC-429-01",
  "message": "Rate limit exceeded for verification",
  "timestamp": "2026-01-29T15:00:00Z",
  "path": "/api/project-configs/verify",
  "details": {
    "limit": 10,
    "window": "1 minute",
    "retryAfter": 45
  }
}
```

---

### 6.3 Server Error Codes (5xx)

#### 500 Internal Server Error

| Error Code | Message | Description | HTTP Status |
|------------|---------|-------------|-------------|
| `PC-500-01` | Internal server error | Generic server error | 500 |
| `PC-500-02` | Encryption service unavailable | Encryption service is down | 500 |
| `PC-500-03` | Database connection error | Cannot connect to database | 500 |
| `PC-500-04` | External service error | Jira/GitHub API error | 500 |

**Example Response:**
```json
{
  "error": "PC-500-02",
  "message": "Encryption service unavailable",
  "timestamp": "2026-01-29T15:00:00Z",
  "path": "/api/project-configs",
  "details": {
    "service": "EncryptionService",
    "reason": "Connection timeout"
  }
}
```

---

#### 503 Service Unavailable

| Error Code | Message | Description | HTTP Status |
|------------|---------|-------------|-------------|
| `PC-503-01` | Service temporarily unavailable | Service is down for maintenance | 503 |
| `PC-503-02` | Database is read-only | Database in maintenance mode | 503 |

**Example Response:**
```json
{
  "error": "PC-503-01",
  "message": "Service temporarily unavailable",
  "timestamp": "2026-01-29T15:00:00Z",
  "path": "/api/project-configs",
  "details": {
    "retryAfter": 300,
    "reason": "Scheduled maintenance"
  }
}
```

---

### 6.4 Error Code Implementation

```java
package com.samt.projectconfig.exception;

public enum ErrorCode {
    
    // 400 Bad Request
    VALIDATION_FAILED("PC-400-01", "Validation failed"),
    INVALID_JIRA_URL("PC-400-02", "Invalid Jira URL format"),
    INVALID_GITHUB_URL("PC-400-03", "Invalid GitHub URL format"),
    INVALID_JIRA_TOKEN("PC-400-04", "Invalid Jira token format"),
    INVALID_GITHUB_TOKEN("PC-400-05", "Invalid GitHub token format"),
    CONFIG_DELETED("PC-400-06", "Config is deleted"),
    CONFIG_NOT_DELETED("PC-400-07", "Config not deleted"),
    
    // 401 Unauthorized
    MISSING_SERVICE_HEADERS("PC-401-01", "Missing service authentication headers"),
    INVALID_SERVICE_KEY("PC-401-02", "Invalid service key"),
    UNKNOWN_SERVICE("PC-401-03", "Unknown service name"),
    TOKEN_EXPIRED("PC-401-04", "JWT token expired"),
    INVALID_TOKEN("PC-401-05", "Invalid JWT token"),
    
    // 403 Forbidden
    NOT_GROUP_LEADER("PC-403-01", "User is not leader of group"),
    INSUFFICIENT_SERVICE_PERMISSION("PC-403-02", "Service does not have READ_DECRYPTED_TOKENS permission"),
    LECTURER_NOT_SUPERVISING("PC-403-03", "Lecturer not supervising group"),
    INSUFFICIENT_ROLE("PC-403-04", "Insufficient role"),
    
    // 404 Not Found
    CONFIG_NOT_FOUND("PC-404-01", "Config not found"),
    CONFIG_NOT_FOUND_FOR_GROUP("PC-404-02", "Config not found for group"),
    GROUP_NOT_FOUND("PC-404-03", "Group not found"),
    
    // 409 Conflict
    CONFIG_ALREADY_EXISTS("PC-409-01", "Config already exists for group"),
    CONCURRENT_MODIFICATION("PC-409-02", "Concurrent modification detected"),
    
    // 422 Unprocessable Entity
    ENCRYPTION_FAILED("PC-422-01", "Token encryption failed"),
    DECRYPTION_FAILED("PC-422-02", "Token decryption failed"),
    INVALID_STATE_TRANSITION("PC-422-03", "Invalid state transition"),
    VERIFICATION_TIMEOUT("PC-422-04", "Verification timeout"),
    
    // 429 Too Many Requests
    RATE_LIMIT_VERIFICATION("PC-429-01", "Rate limit exceeded for verification"),
    RATE_LIMIT_API("PC-429-02", "Rate limit exceeded for API"),
    
    // 500 Internal Server Error
    INTERNAL_ERROR("PC-500-01", "Internal server error"),
    ENCRYPTION_SERVICE_UNAVAILABLE("PC-500-02", "Encryption service unavailable"),
    DATABASE_ERROR("PC-500-03", "Database connection error"),
    EXTERNAL_SERVICE_ERROR("PC-500-04", "External service error"),
    
    // 503 Service Unavailable
    SERVICE_UNAVAILABLE("PC-503-01", "Service temporarily unavailable"),
    DATABASE_READ_ONLY("PC-503-02", "Database is read-only");
    
    private final String code;
    private final String message;
    
    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
}
```

---

### 6.5 Exception with Error Code

```java
package com.samt.projectconfig.exception;

import lombok.Getter;

@Getter
public class ProjectConfigException extends RuntimeException {
    
    private final ErrorCode errorCode;
    private final Object details;
    
    public ProjectConfigException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }
    
    public ProjectConfigException(ErrorCode errorCode, Object details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = details;
    }
    
    public ProjectConfigException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.details = null;
    }
    
    public ProjectConfigException(ErrorCode errorCode, String customMessage, Object details) {
        super(customMessage);
        this.errorCode = errorCode;
        this.details = details;
    }
}
```

---

### 6.6 Global Exception Handler with Error Codes

```java
package com.samt.projectconfig.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handle ProjectConfigException with error codes
     */
    @ExceptionHandler(ProjectConfigException.class)
    public ResponseEntity<ErrorResponse> handleProjectConfigException(
            ProjectConfigException ex,
            WebRequest request) {
        
        HttpStatus status = mapErrorCodeToHttpStatus(ex.getErrorCode());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .error(ex.getErrorCode().getCode())
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false).replace("uri=", ""))
            .details(ex.getDetails())
            .build();
        
        log.error("ProjectConfigException: {} - {}", ex.getErrorCode().getCode(), ex.getMessage(), ex);
        
        return ResponseEntity.status(status).body(errorResponse);
    }
    
    /**
     * Handle validation errors with PC-400-01
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> FieldErrorDetail.builder()
                .field(error.getField())
                .message(error.getDefaultMessage())
                .rejectedValue(error.getRejectedValue())
                .build())
            .toList();
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .error(ErrorCode.VALIDATION_FAILED.getCode())
            .message(ErrorCode.VALIDATION_FAILED.getMessage())
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false).replace("uri=", ""))
            .errors(fieldErrors)
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Map error code to HTTP status
     */
    private HttpStatus mapErrorCodeToHttpStatus(ErrorCode errorCode) {
        String code = errorCode.getCode();
        
        if (code.startsWith("PC-400")) return HttpStatus.BAD_REQUEST;
        if (code.startsWith("PC-401")) return HttpStatus.UNAUTHORIZED;
        if (code.startsWith("PC-403")) return HttpStatus.FORBIDDEN;
        if (code.startsWith("PC-404")) return HttpStatus.NOT_FOUND;
        if (code.startsWith("PC-409")) return HttpStatus.CONFLICT;
        if (code.startsWith("PC-422")) return HttpStatus.UNPROCESSABLE_ENTITY;
        if (code.startsWith("PC-429")) return HttpStatus.TOO_MANY_REQUESTS;
        if (code.startsWith("PC-500")) return HttpStatus.INTERNAL_SERVER_ERROR;
        if (code.startsWith("PC-503")) return HttpStatus.SERVICE_UNAVAILABLE;
        
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
```

---

### 6.7 Error Response DTO

```java
package com.samt.projectconfig.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    /**
     * Error code (e.g., PC-403-01)
     */
    private String error;
    
    /**
     * Human-readable error message
     */
    private String message;
    
    /**
     * Timestamp when error occurred
     */
    private LocalDateTime timestamp;
    
    /**
     * Request path that caused the error
     */
    private String path;
    
    /**
     * Field-level validation errors (for PC-400-01)
     */
    private List<FieldErrorDetail> errors;
    
    /**
     * Additional error details (optional)
     */
    private Object details;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class FieldErrorDetail {
    private String field;
    private String message;
    private Object rejectedValue;
}
```

---

### 6.8 Usage Examples

#### Example 1: Throw exception with error code

```java
@Service
public class ProjectConfigService {
    
    public ProjectConfig getConfigByGroup(UUID groupId, UUID userId) {
        // Check if group exists
        if (!groupRepository.existsById(groupId)) {
            throw new ProjectConfigException(
                ErrorCode.GROUP_NOT_FOUND,
                Map.of("groupId", groupId)
            );
        }
        
        // Check if user is leader
        if (!isLeaderOfGroup(userId, groupId)) {
            throw new ProjectConfigException(
                ErrorCode.NOT_GROUP_LEADER,
                Map.of(
                    "userId", userId,
                    "groupId", groupId
                )
            );
        }
        
        // Get config
        return projectConfigRepository.findByGroupId(groupId)
            .orElseThrow(() -> new ProjectConfigException(
                ErrorCode.CONFIG_NOT_FOUND_FOR_GROUP,
                Map.of("groupId", groupId)
            ));
    }
}
```

#### Example 2: Internal API authentication error

```java
@Component
public class InternalServiceAuthFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) {
        
        String serviceName = request.getHeader("X-Service-Name");
        String serviceKey = request.getHeader("X-Service-Key");
        
        if (serviceName == null || serviceKey == null) {
            throw new ProjectConfigException(
                ErrorCode.MISSING_SERVICE_HEADERS,
                Map.of("requiredHeaders", List.of("X-Service-Name", "X-Service-Key"))
            );
        }
        
        if (!isValidServiceKey(serviceName, serviceKey)) {
            throw new ProjectConfigException(
                ErrorCode.INVALID_SERVICE_KEY,
                Map.of("serviceName", serviceName)
            );
        }
        
        filterChain.doFilter(request, response);
    }
}
```

---

### 6.9 Error Code Quick Reference

| HTTP Status | Count | Code Range | Purpose |
|-------------|-------|------------|---------|
| 400 | 7 | PC-400-01 to PC-400-07 | Client validation errors |
| 401 | 5 | PC-401-01 to PC-401-05 | Authentication failures |
| 403 | 4 | PC-403-01 to PC-403-04 | Authorization failures |
| 404 | 3 | PC-404-01 to PC-404-03 | Resource not found |
| 409 | 2 | PC-409-01 to PC-409-02 | Conflict errors |
| 422 | 4 | PC-422-01 to PC-422-04 | Unprocessable entity |
| 429 | 2 | PC-429-01 to PC-429-02 | Rate limiting |
| 500 | 4 | PC-500-01 to PC-500-04 | Server errors |
| 503 | 2 | PC-503-01 to PC-503-02 | Service unavailable |
| **Total** | **33** | | |
