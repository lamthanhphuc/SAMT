# Identity Service – Package Structure & Security

## 1. Package Structure

```
com.example.identityservice
 ├── controller
 │    └── AuthController
 ├── service
 │    ├── AuthService
 │    ├── JwtService
 │    └── RefreshTokenService
 ├── repository
 │    ├── UserRepository
 │    └── RefreshTokenRepository
 ├── entity
 │    ├── User
 │    └── RefreshToken
 ├── dto
 │    ├── RegisterRequest
 │    ├── RegisterResponse
 │    ├── UserDto
 │    ├── LoginRequest
 │    ├── LoginResponse
 │    ├── RefreshTokenRequest
 │    ├── LogoutRequest
 │    └── TokenResponse
 ├── exception
 │    ├── InvalidCredentialsException
 │    ├── TokenExpiredException
 │    ├── EmailAlreadyExistsException
 │    └── AccountLockedException
 ├── validation
 │    ├── PasswordValidator
 │    └── EmailValidator
 └── security
      ├── JwtAuthenticationFilter
      └── SecurityConfig
```

---

## 2. Security Configuration Assumptions

### Session Management

- ✅ **Stateless session**
- ✅ **CSRF disabled** (stateless API)
- ✅ **Authentication via JWT filter**

### Authentication

- **Login**: Manual authentication using `PasswordEncoder`
- **No HttpSession**: Completely stateless

### Password

- **Encoder**: `BCryptPasswordEncoder`
- **Strength**: 10 rounds

### Authorization

- **Method**: Role-based via `hasRole()`
- **Role Prefix**: `ROLE_`
- **Example**: `@PreAuthorize("hasRole('ADMIN')")`

---

## 3. Security Filter Chain

```java
HTTP Request
    ↓
JwtAuthenticationFilter (validate JWT)
    ↓
SecurityContextHolder (set authentication)
    ↓
Controller (@PreAuthorize check)
    ↓
Response
```

---

## ✅ Status

**READY FOR SPRING BOOT IMPLEMENTATION**

