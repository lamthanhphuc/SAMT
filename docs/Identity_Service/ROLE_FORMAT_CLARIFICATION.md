# Role Format Clarification

**Last Updated:** January 30, 2026  
**Purpose:** Clarify role format across different layers of the application

---

## ⚠️ IMPORTANT: Role Format Varies by Layer

The format of roles changes depending on where they are used in the application:

| Layer | Format | Example | Where Used |
|-------|--------|---------|------------|
| **Database** | Plain string | `ADMIN`, `STUDENT`, `LECTURER` | PostgreSQL `users.role` column |
| **Java Entity** | Enum | `Role.ADMIN`, `Role.STUDENT` | `User.java` entity class |
| **JWT Token** | Array of strings | `["ADMIN"]`, `["STUDENT"]` | JWT payload claims |
| **Spring Security** | Granted Authority | `ROLE_ADMIN`, `ROLE_STUDENT` | Internal Spring Security context |
| **@PreAuthorize** | hasRole() param | `hasRole('ADMIN')` | Controller annotations |

---

## 🔍 Detailed Breakdown

### 1. Database Layer (PostgreSQL)

**Schema:**
```sql
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  role VARCHAR(50) NOT NULL,  -- Values: 'ADMIN', 'STUDENT', 'LECTURER'
  ...
);
```

**Example Row:**
```
id | email              | role
---|--------------------|--------
1  | admin@example.com  | ADMIN
2  | student@univ.edu   | STUDENT
3  | lecturer@univ.edu  | LECTURER
```

✅ **NO** `ROLE_` prefix in database  
✅ Just plain enum value: `ADMIN`, `STUDENT`, `LECTURER`

---

### 2. Java Entity Layer

**User.java:**
```java
@Entity
public class User {
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;  // Enum: Role.ADMIN, Role.STUDENT, Role.LECTURER
    
    public enum Role {
        ADMIN,
        LECTURER,
        STUDENT
    }
}
```

✅ Direct mapping from database  
✅ No prefix needed

---

### 3. JWT Token Layer

**JwtService.java (Token Generation):**
```java
public String generateAccessToken(User user) {
    return Jwts.builder()
        .subject(String.valueOf(user.getId()))
        .claim("email", user.getEmail())
        .claim("roles", List.of(user.getRole().name()))  // ["ADMIN"] NO prefix!
        .claim("token_type", "ACCESS")
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 900000))
        .signWith(secretKey)
        .compact();
}
```

**JWT Payload (decoded at jwt.io):**
```json
{
  "sub": "1",
  "email": "admin@example.com",
  "roles": ["ADMIN"],           // ← NO ROLE_ prefix
  "token_type": "ACCESS",
  "iat": 1706612400,
  "exp": 1706613300
}
```

✅ Roles stored as plain strings in array  
✅ **NO** `ROLE_` prefix in JWT claims

---

### 4. Spring Security Layer (Internal)

**JwtAuthenticationFilter.java:**
```java
@Override
protected void doFilterInternal(HttpServletRequest request, ...) {
    // ... validate JWT ...
    
    User user = userRepository.findById(userId).orElse(null);
    
    if (user != null && user.getStatus() == User.Status.ACTIVE) {
        // Create authentication with ROLE_ prefix
        UsernamePasswordAuthenticationToken authToken = 
            new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                //                                    ^^^^^^ Prefix added here!
            );
        
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
```

**SecurityContext (internal state):**
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
auth.getAuthorities();  // Returns: [ROLE_ADMIN] ← Prefix added internally
```

✅ `ROLE_` prefix added when creating `GrantedAuthority`  
✅ This is Spring Security's internal requirement  
✅ Prefix ONLY exists in SecurityContext, NOT in database/JWT

---

### 5. Controller Authorization Layer

**AdminController.java:**
```java
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")  // ← Use plain 'ADMIN', NOT 'ROLE_ADMIN'
public class AdminController {
    
    @PostMapping("/users")
    public ResponseEntity<?> createUser(...) {
        // Spring Security automatically:
        // 1. Checks SecurityContext for authority
        // 2. Looks for "ROLE_ADMIN" (adds ROLE_ prefix)
        // 3. Allows access if user has ROLE_ADMIN authority
    }
}
```

✅ Use `hasRole('ADMIN')` in `@PreAuthorize`  
✅ Spring automatically adds `ROLE_` prefix when checking  
❌ **DO NOT** use `hasRole('ROLE_ADMIN')` (double prefix!)

---

## 🎯 Key Takeaways

1. **Database & JWT**: Store plain role names (`ADMIN`, `STUDENT`, `LECTURER`)
2. **Spring Security Internal**: Adds `ROLE_` prefix when creating authorities
3. **@PreAuthorize**: Use `hasRole('ADMIN')`, Spring auto-adds prefix
4. **Never store**: `ROLE_` prefix in database or JWT claims

---

## 🐛 Common Mistakes

### ❌ WRONG: Storing ROLE_ in database
```sql
INSERT INTO users (email, role) 
VALUES ('admin@example.com', 'ROLE_ADMIN');  -- ❌ WRONG!
```

### ✅ CORRECT: Plain enum value
```sql
INSERT INTO users (email, role) 
VALUES ('admin@example.com', 'ADMIN');  -- ✅ CORRECT
```

---

### ❌ WRONG: ROLE_ prefix in JWT
```java
.claim("roles", List.of("ROLE_" + user.getRole().name()))  // ❌ WRONG!
// Results in: {"roles": ["ROLE_ADMIN"]}
```

### ✅ CORRECT: Plain enum value in JWT
```java
.claim("roles", List.of(user.getRole().name()))  // ✅ CORRECT
// Results in: {"roles": ["ADMIN"]}
```

---

### ❌ WRONG: Double prefix in @PreAuthorize
```java
@PreAuthorize("hasRole('ROLE_ADMIN')")  // ❌ WRONG! Double prefix
```

### ✅ CORRECT: Single prefix (auto-added)
```java
@PreAuthorize("hasRole('ADMIN')")  // ✅ CORRECT
```

---

## 🔄 Flow Diagram

```
┌─────────────┐
│  Database   │  role = "ADMIN"
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ Java Entity │  Role.ADMIN
└──────┬──────┘
       │
       ├──────────────────────┐
       │                      │
       ▼                      ▼
┌─────────────┐      ┌──────────────────┐
│  JWT Token  │      │ Spring Security  │
│ ["ADMIN"]   │      │ "ROLE_ADMIN"     │
└─────────────┘      └────────┬─────────┘
                              │
                              ▼
                     ┌──────────────────┐
                     │  @PreAuthorize   │
                     │ hasRole('ADMIN') │
                     └──────────────────┘
```

---

## 📚 References

- [Authentication-Authorization-Design.md](./Authentication-Authorization-Design.md) - Section 5: Roles & Authorities
- [Database-Design.md](./Database-Design.md) - Table: users
- [API_CONTRACT.md](./API_CONTRACT.md) - JWT Token Format
- [IMPLEMENTATION_GUIDE.md](./IMPLEMENTATION_GUIDE.md) - JWT Generation

---

## ✅ Verification Checklist

- [ ] Database `users.role` column stores plain enum (`ADMIN`, `STUDENT`, `LECTURER`)
- [ ] JWT token `roles` claim contains array without prefix (`["ADMIN"]`)
- [ ] `JwtAuthenticationFilter` adds `ROLE_` prefix when creating `SimpleGrantedAuthority`
- [ ] `@PreAuthorize` uses `hasRole('ADMIN')` NOT `hasRole('ROLE_ADMIN')`
- [ ] All documentation updated to reflect correct format

---

**Questions?** Contact: Backend Team Lead
