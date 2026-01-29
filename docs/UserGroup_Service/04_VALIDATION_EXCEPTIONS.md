# Validation & Exception Handling - User & Group Service

## PART 1: VALIDATION RULES

### 1. User Validation

#### 1.1 Email Validation

- **Format:** Must be valid email format (RFC 5322)
- **Length:** Max 255 characters
- **Unique:** Must be unique trong hệ thống
- **Required:** YES

**Example:**
```
✅ valid@example.com
✅ user.name@company.co.uk
❌ invalid@
❌ @example.com
❌ no-domain@
```

#### 1.2 Full Name Validation

- **Length:** Min 2, Max 100 characters
- **Pattern:** Chỉ chấp nhận letters, spaces, và dấu tiếng Việt
- **Required:** YES
- **Trim:** Loại bỏ leading/trailing spaces

**Example:**
```
✅ Nguyễn Văn A
✅ John Doe
❌ A (too short)
❌ 123456 (no numbers)
```

#### 1.3 Status Validation

- **Enum:** ACTIVE, INACTIVE
- **Default:** ACTIVE khi tạo mới
- **Immutable:** Không cho đổi sang INACTIVE nếu user đang là LEADER

---

### 2. Group Validation

#### 2.1 Group Name Validation

- **Length:** Min 3, Max 50 characters
- **Pattern:** Alphanumeric + hyphen + underscore
- **Format:** `[A-Z]{2,4}[0-9]{2,4}-G[0-9]+`
- **Example:** SE1705-G1, FPT2024-G10
- **Unique:** (groupName + semester) phải unique

**Example:**
```
✅ SE1705-G1
✅ AI2024-G999
❌ SE (too short)
❌ Group 1 (có space)
❌ SE-G1 (thiếu số học kỳ)
```

#### 2.2 Semester Validation

- **Length:** Min 4, Max 20 characters
- **Pattern:** `(Spring|Summer|Fall|Winter)[0-9]{4}`
- **Required:** YES
- **Immutable:** Không cho update sau khi tạo

**Example:**
```
✅ Spring2026
✅ Fall2025
❌ Q1-2026 (wrong format)
❌ Spring26 (năm phải 4 số)
```

#### 2.3 Lecturer ID Validation

- **Type:** UUID
- **Required:** YES
- **Constraint:** Phải tồn tại trong bảng users
- **Role Check:** User phải có role LECTURER

---

### 3. Group Member Validation

#### 3.1 Add Member Validation

- **userId:** 
  - Required: YES
  - Must exist in users table
  - Status must be ACTIVE
  - Cannot be INACTIVE
  
- **isLeader:**
  - Type: Boolean
  - Default: false
  - If true: check no existing LEADER in group

#### 3.2 Remove Member Validation

- **Pre-check:**
  - Member must exist in group
  - If member is LEADER: group must have ≤ 1 member (only the leader)
  - Cannot remove if group has other members

#### 3.3 Group Role Assignment Validation

- **role:**
  - Required: YES
  - Enum: LEADER, MEMBER
  - If LEADER: must not have existing LEADER (or auto-demote)

---

### 4. Request Validation Annotations

#### 4.1 User DTOs

```java
public class UpdateUserRequest {
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    @Pattern(regexp = "^[a-zA-ZÀ-ỹ\\s]+$", message = "Full name contains invalid characters")
    private String fullName;
}
```

#### 4.2 Group DTOs

```java
public class CreateGroupRequest {
    @NotBlank(message = "Group name is required")
    @Size(min = 3, max = 50, message = "Group name must be between 3 and 50 characters")
    @Pattern(regexp = "^[A-Z]{2,4}[0-9]{2,4}-G[0-9]+$", message = "Invalid group name format")
    private String groupName;
    
    @NotBlank(message = "Semester is required")
    @Pattern(regexp = "^(Spring|Summer|Fall|Winter)[0-9]{4}$", message = "Invalid semester format")
    private String semester;
    
    @NotNull(message = "Lecturer ID is required")
    private UUID lecturerId;
}
```

#### 4.3 Member DTOs

```java
public class AddMemberRequest {
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotNull(message = "isLeader is required")
    private Boolean isLeader;
}

public class AssignRoleRequest {
    @NotNull(message = "Role is required")
    @Pattern(regexp = "^(LEADER|MEMBER)$", message = "Role must be LEADER or MEMBER")
    private String role;
}
```

---

### 5. Business Logic Validation

#### 5.1 Semester Uniqueness Check

```sql
-- Check if user already in a group for this semester
SELECT COUNT(*) FROM user_groups ug
JOIN groups g ON ug.group_id = g.id
WHERE ug.user_id = :userId 
  AND g.semester = :semester
  AND ug.deleted_at IS NULL
  AND g.deleted_at IS NULL
```

**Rule:** Count must be 0

#### 5.2 Leader Uniqueness Check

```sql
-- Check if group already has a leader
SELECT COUNT(*) FROM user_groups
WHERE group_id = :groupId
  AND role = 'LEADER'
  AND deleted_at IS NULL
```

**Rule:** Count must be 0 (before assigning new leader)

#### 5.3 Active Members Count

```sql
-- Count active members in group (excluding leader)
SELECT COUNT(*) FROM user_groups
WHERE group_id = :groupId
  AND role = 'MEMBER'
  AND deleted_at IS NULL
```

**Rule:** Must be 0 before allowing leader removal

---

### 6. Global Validation Rules

1. **UUID Format:** All IDs must be valid UUID v4
2. **Null Handling:** Required fields cannot be null or empty
3. **Trimming:** All string inputs are trimmed before validation
4. **Case Sensitivity:** Email is case-insensitive, others are case-sensitive
5. **Soft Delete:** Always check `deleted_at IS NULL` in queries
6. **existsById Caveat:** Do NOT use JPA `existsById()` for soft-delete entities — use `findById().isPresent()` instead

---

## PART 2: EXCEPTION HANDLING

### 1. Exception Hierarchy

```
Exception
 └── RuntimeException
      └── BaseException (abstract)
           ├── ResourceNotFoundException
           ├── ConflictException
           ├── UnauthorizedException
           ├── ForbiddenException
           └── ValidationException
```

---

### 2. Custom Exception Classes

#### 2.1 BaseException

```java
@Getter
public abstract class BaseException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    
    protected BaseException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
```

#### 2.2 ResourceNotFoundException

```java
public class ResourceNotFoundException extends BaseException {
    public ResourceNotFoundException(String code, String message) {
        super(code, message, HttpStatus.NOT_FOUND);
    }
    
    // Factory methods
    public static ResourceNotFoundException userNotFound(UUID userId) {
        return new ResourceNotFoundException(
            "USER_NOT_FOUND", 
            String.format("User with ID %s not found", userId)
        );
    }
    
    public static ResourceNotFoundException groupNotFound(UUID groupId) {
        return new ResourceNotFoundException(
            "GROUP_NOT_FOUND", 
            String.format("Group with ID %s not found", groupId)
        );
    }
    
    public static ResourceNotFoundException lecturerNotFound(UUID lecturerId) {
        return new ResourceNotFoundException(
            "LECTURER_NOT_FOUND", 
            String.format("Lecturer with ID %s not found", lecturerId)
        );
    }
}
```

#### 2.3 ConflictException

```java
public class ConflictException extends BaseException {
    public ConflictException(String code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }
    
    // Factory methods
    public static ConflictException userAlreadyInGroup(UUID userId, UUID groupId) {
        return new ConflictException(
            "USER_ALREADY_IN_GROUP",
            String.format("User %s is already in group %s", userId, groupId)
        );
    }
    
    public static ConflictException userAlreadyInGroupSameSemester(UUID userId, String semester) {
        return new ConflictException(
            "USER_ALREADY_IN_GROUP_SAME_SEMESTER",
            String.format("User %s is already in a group for semester %s", userId, semester)
        );
    }
    
    public static ConflictException userInactive(UUID userId) {
        return new ConflictException(
            "USER_INACTIVE",
            String.format("User %s is inactive", userId)
        );
    }
    
    public static ConflictException leaderAlreadyExists(UUID groupId) {
        return new ConflictException(
            "LEADER_ALREADY_EXISTS",
            String.format("Group %s already has a leader", groupId)
        );
    }
    
    public static ConflictException cannotRemoveLeader() {
        return new ConflictException(
            "CANNOT_REMOVE_LEADER",
            "Cannot remove leader while group has active members"
        );
    }
    
    public static ConflictException groupNameDuplicate(String groupName, String semester) {
        return new ConflictException(
            "GROUP_NAME_DUPLICATE",
            String.format("Group name %s already exists in semester %s", groupName, semester)
        );
    }
}
```

#### 2.4 UnauthorizedException

```java
public class UnauthorizedException extends BaseException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }
}
```

#### 2.5 ForbiddenException

```java
public class ForbiddenException extends BaseException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }
    
    public static ForbiddenException insufficientPermission() {
        return new ForbiddenException("You do not have permission to perform this action");
    }
    
    public static ForbiddenException cannotAccessOtherUser() {
        return new ForbiddenException("You can only access your own profile");
    }
    
    public static ForbiddenException lecturerCannotUpdateProfile() {
        return new ForbiddenException("Lecturers cannot update profiles via this API");
    }
}
```

---

### 3. Global Exception Handler

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex) {
        log.error("Business exception: {} - {}", ex.getCode(), ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ex.getCode())
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();
            
        return ResponseEntity.status(ex.getStatus()).body(response);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage())
        );
        
        ErrorResponse response = ErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .message("Validation failed")
            .timestamp(Instant.now())
            .errors(errors)
            .build();
            
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        
        ErrorResponse response = ErrorResponse.builder()
            .code("INTERNAL_SERVER_ERROR")
            .message("An unexpected error occurred")
            .timestamp(Instant.now())
            .build();
            
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
```

---

### 4. Error Response DTO

```java
@Data
@Builder
public class ErrorResponse {
    private String code;
    private String message;
    private Instant timestamp;
    private Map<String, String> errors; // For validation errors
}
```

---

### 5. Validation Error Response Format

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "timestamp": "2026-01-01T10:00:00Z",
  "errors": [
    {
      "field": "fullName",
      "message": "Full name is required"
    },
    {
      "field": "email",
      "message": "Invalid email format"
    }
  ]
}
```

---

### 6. Logging Strategy

#### 6.1 Log Levels

- **ERROR:** Unexpected exceptions, system errors
- **WARN:** Business exceptions, validation failures
- **INFO:** Successful operations, state changes
- **DEBUG:** Detailed flow, method entry/exit

#### 6.2 Log Format

```java
// Success
log.info("User {} successfully added to group {}", userId, groupId);

// Business exception
log.warn("Failed to add user {} to group {}: USER_INACTIVE", userId, groupId);

// System error
log.error("Database connection failed while creating group", ex);
```

---

### 7. Transaction Rollback Rules

#### 7.1 Rollback on All Exceptions

```java
@Transactional(rollbackFor = Exception.class)
public void assignGroupRole(UUID groupId, UUID userId, String role) {
    // Implementation
}
```

#### 7.2 No Rollback for Read Operations

```java
@Transactional(readOnly = true)
public UserResponse getUserById(UUID userId) {
    // Implementation
}
```
