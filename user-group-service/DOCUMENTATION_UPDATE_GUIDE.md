# Documentation Update Guide
**Based On:** CODE_REVIEW_FINAL.md (Implementation Source of Truth)  
**Date:** January 31, 2026  
**Type:** Clarifications & Gap Filling (No Behavioral Changes)

---

## SUMMARY

**Purpose:** Synchronize documentation with actual implementation  
**Approach:** Targeted updates - only add/clarify missing or ambiguous parts  
**Source of Truth:** Reviewed code implementation

**Updates Required:** 5 files, 8 sections

---

## FILE 1: 02_DESIGN.md

### Update 1.1: UC26 - Remove Member Logic Clarification

**Section:** Add to existing UC26 implementation section (around line 800-900)

**Location:** After existing UC26 description

**Content to Add:**

```markdown
#### UC26: Business Rule - Remove Leader Validation

**Implementation Detail:**

When removing a user with LEADER role, system checks if group has other active members:

```java
// Query counts MEMBER role only (excludes LEADER being removed)
long memberCount = userGroupRepository.countMembersByGroupId(groupId);
if (memberCount > 0) {
    throw ConflictException.cannotRemoveLeader();
}
```

**Query Specification:**
```sql
-- countMembersByGroupId() implementation
SELECT COUNT(ug) FROM UserGroup ug 
WHERE ug.groupId = :groupId AND ug.role = 'MEMBER'
-- Note: Excludes LEADER role
```

**Business Logic:**
- LEADER can be removed if group has 0 MEMBER (only LEADER exists)
- LEADER cannot be removed if group has 1+ MEMBER
- Method name `countMembersByGroupId()` refers to MEMBER role specifically, not all members

**Test Cases:**
- Remove LEADER from empty group (0 MEMBER) → 204 SUCCESS
- Remove LEADER from group with 1 MEMBER → 409 CANNOT_REMOVE_LEADER
- Remove MEMBER from group → 204 SUCCESS (no validation)

**Recommendation (Optional):** Consider renaming to `countNonLeaderMembersByGroupId()` for clarity.
```

**Type:** CLARIFICATION (existing behavior)

---

### Update 1.2: UC25 - Concurrency Control Implementation

**Section:** Existing UC25 section (around line 700-800)

**Location:** Add sub-section after UC25 basic description

**Content to Add:**

```markdown
#### UC25: Concurrency Control - Race Condition Prevention

**Implementation Strategy:**

Two-layer protection against concurrent LEADER assignment:

**Layer 1: Transaction Isolation**
```java
@Transactional(rollbackFor = Exception.class, isolation = Isolation.SERIALIZABLE)
```
- Highest isolation level (SERIALIZABLE)
- Prevents phantom reads and non-repeatable reads
- Ensures serializable execution of concurrent transactions

**Layer 2: Pessimistic Locking**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT ug FROM UserGroup ug WHERE ug.groupId = :groupId AND ug.role = 'LEADER'")
Optional<UserGroup> findLeaderByGroupIdWithLock(@Param("groupId") UUID groupId);
```
- Database-level row lock (SELECT ... FOR UPDATE)
- Blocks concurrent reads until transaction commits
- Prevents race condition when demoting old leader

**Execution Flow:**

| Step | Request A | Request B |
|------|-----------|-----------|
| 1 | Acquire lock on old leader | Waits for lock |
| 2 | Demote old leader to MEMBER | Still waiting |
| 3 | Assign new user as LEADER | Still waiting |
| 4 | Commit transaction (release lock) | Acquires lock |
| 5 | - | Sees no old leader (already demoted) |
| 6 | - | Assigns user as LEADER → SUCCESS |

**Race Condition Scenario (Without Lock):**
- 2 concurrent requests could both:
  1. Read same old leader
  2. Demote to MEMBER (duplicate operation)
  3. Assign different users as LEADER
  4. Result: 2 LEADERS in same group ❌

**With Lock (Current Implementation):**
- Request B blocks on step 1 until Request A completes
- Only one demote operation executes
- Second request sees correct state
- Result: 1 LEADER in group ✅

**Trade-offs:**
- ✅ Strong consistency guarantee
- ⚠️ Reduced concurrency (requests serialize)
- ⚠️ Potential deadlock if not careful with transaction boundaries

**Alternative Considered:** Application-level synchronized block - rejected due to horizontal scaling concerns.
```

**Type:** CLARIFICATION + DESIGN RATIONALE

---

### Update 1.3: Delete Group Behavior

**Section:** Create new section or add to existing Group lifecycle

**Location:** After UC23 Create Group section

**Content to Add:**

```markdown
#### Delete Group - Cascade Soft Delete Behavior

**Current Implementation:**

When ADMIN deletes a group, system performs cascade soft delete:

```java
@Transactional
public void deleteGroup(UUID groupId) {
    // 1. Soft delete group
    group.softDelete(); // Sets deleted_at = NOW()
    groupRepository.save(group);
    
    // 2. Cascade soft delete all memberships
    List<UserGroup> memberships = userGroupRepository.findAllByGroupId(groupId);
    memberships.forEach(UserGroup::softDelete);
    userGroupRepository.saveAll(memberships);
}
```

**Business Rule (Current):**
- Delete group ALLOWED regardless of member count
- All memberships automatically soft deleted (cascade)
- No orphaned memberships remain

**Behavior:**
- Group with 0 members → Soft delete group
- Group with N members → Soft delete group + all N memberships
- Students see group as "deleted" in their group list
- Historical data preserved (soft delete, not hard delete)

**Recommendation (Optional - Requires Business Decision):**

Consider preventing delete if group has active members:

```java
long memberCount = userGroupRepository.countAllMembersByGroupId(groupId);
if (memberCount > 0) {
    throw new ConflictException("CANNOT_DELETE_GROUP_WITH_MEMBERS", 
            "Group has " + memberCount + " members. Remove all members first.");
}
```

**Rationale:** 
- Prevents accidental data loss
- Follows "least surprise" principle
- Requires explicit member removal first

**Decision Needed:** Consult with product owner on preferred behavior.

**Current Status:** Cascade delete implemented and working correctly.
```

**Type:** CLARIFICATION + RECOMMENDATION

---

## FILE 2: 03_API_SPECIFICATION.md

### Update 2.1: gRPC Error Mapping Table

**Section:** Add after "Common Error Response Format" (around line 20-30)

**Location:** Before Use Case API sections

**Content to Add:**

```markdown
### gRPC Status → HTTP Status Mapping (Implementation Reference)

**All gRPC calls to Identity Service map errors as follows:**

| gRPC Status Code | HTTP Status | Error Code | Use Case | Implementation |
|------------------|-------------|------------|----------|----------------|
| OK | 200 | - | Success | Return data |
| NOT_FOUND | 404 | USER_NOT_FOUND, LECTURER_NOT_FOUND | UC21, UC23, UC24, UC27 | ResourceNotFoundException |
| PERMISSION_DENIED | 403 | FORBIDDEN | All | ForbiddenException |
| UNAVAILABLE | 503 | SERVICE_UNAVAILABLE | All gRPC calls | ServiceUnavailableException |
| DEADLINE_EXCEEDED | 504 | GATEWAY_TIMEOUT | All gRPC calls (>5s) | GatewayTimeoutException |
| INVALID_ARGUMENT | 400 | BAD_REQUEST | Data validation | BadRequestException |
| FAILED_PRECONDITION | 409 | CONFLICT | Business rule violation | ConflictException |
| INTERNAL | 500 | INTERNAL_ERROR | Unexpected errors | RuntimeException |
| UNAUTHENTICATED | 401 | UNAUTHORIZED | Invalid credentials | UnauthorizedException |

**Implementation Pattern:**

```java
try {
    // gRPC call to Identity Service
    GetUserResponse user = identityServiceClient.getUser(userId);
    return processUser(user);
    
} catch (StatusRuntimeException e) {
    switch (e.getStatus().getCode()) {
        case UNAVAILABLE:
            throw ServiceUnavailableException.identityServiceUnavailable(); // 503
        case DEADLINE_EXCEEDED:
            throw GatewayTimeoutException.identityServiceTimeout(); // 504
        case NOT_FOUND:
            throw new ResourceNotFoundException("USER_NOT_FOUND", "User not found"); // 404
        case PERMISSION_DENIED:
            throw new ForbiddenException("FORBIDDEN", "Access denied"); // 403
        default:
            throw new RuntimeException("Unexpected gRPC error: " + e.getStatus());
    }
}
```

**Timeout Configuration:**
- Default gRPC deadline: 5 seconds
- Configurable via `grpc.client.identity-service.deadline`

**Retry Strategy:**
- 503 SERVICE_UNAVAILABLE: Client should retry with exponential backoff
- 504 GATEWAY_TIMEOUT: Client should retry (may be transient)
- 4xx errors: Do not retry (client error)
- 500 errors: Do not retry (server error)
```

**Type:** IMPLEMENTATION CLARIFICATION

---

### Update 2.2: UC26 Delete Member Error Codes

**Section:** Existing UC26 API specification

**Location:** After UC26 endpoint definition

**Content to Add:**

```markdown
#### Error Codes

- `404 GROUP_NOT_FOUND` - Group không tồn tại
- `404 MEMBERSHIP_NOT_FOUND` - User không phải member của group
- `409 CANNOT_REMOVE_LEADER` - Không thể remove LEADER khi group còn active members (role=MEMBER)
- `401 UNAUTHORIZED` - Not authenticated
- `403 FORBIDDEN` - Not ADMIN role

**Note:** Validation checks if LEADER is being removed:
- If group has 1+ users with role=MEMBER → 409 CANNOT_REMOVE_LEADER
- If group has 0 users with role=MEMBER (only LEADER exists) → 204 SUCCESS
```

**Type:** CLARIFICATION

---

## FILE 3: 04_VALIDATION_EXCEPTIONS.md

### Update 3.1: UC24 Race Condition Note

**Section:** Existing UC24 validation section

**Location:** After existing UC24 validation rules

**Content to Add:**

```markdown
#### UC24: Concurrency Consideration

**Validation Logic:**

```java
// Check user not already in semester
if (userGroupRepository.existsByUserIdAndSemester(userId, semester)) {
    throw ConflictException.userAlreadyInGroupSameSemester();
}
```

**Potential Race Condition:**

Under high concurrency, time-of-check-time-of-use (TOCTOU) gap exists:

| Timeline | Request A | Request B |
|----------|-----------|-----------|
| T1 | Check: user not in semester ✓ | - |
| T2 | - | Check: user not in semester ✓ |
| T3 | Insert membership | - |
| T4 | - | Insert membership |
| T5 | Commit | Commit |
| Result | SUCCESS | SUCCESS (DUPLICATE) ❌ |

**Current Mitigation:**

Application-level check prevents most cases.

**Recommended Enhancement (Optional):**

Add database unique constraint for defense-in-depth:

```sql
-- PostgreSQL partial unique index
CREATE UNIQUE INDEX idx_user_groups_user_semester 
ON user_groups (user_id, (
    SELECT semester FROM groups WHERE id = group_id
)) 
WHERE deleted_at IS NULL;
```

**Note:** Requires database schema migration. Constraint would catch race condition at database level and return 409 CONFLICT.

**Risk Assessment:**
- **Likelihood:** LOW (requires exact timing + high load)
- **Impact:** MEDIUM (violates BR-01: one group per semester)
- **Current Status:** Working correctly under normal load
- **Recommendation:** Add constraint before production high-load deployment

**Decision:** Optional - evaluate based on expected load profile.
```

**Type:** CLARIFICATION + RECOMMENDATION

---

### Update 3.2: gRPC Error Handling Patterns

**Section:** Add new section after existing validation rules

**Location:** End of document or create new "Part 4: Cross-Service Error Handling"

**Content to Add:**

```markdown
## Part 4: Cross-Service Error Handling

### gRPC Call Error Mapping

**All services implement consistent gRPC error handling via helper method:**

```java
/**
 * Maps gRPC exceptions to HTTP exceptions per API specification.
 * Used by all service methods that call Identity Service.
 */
private <T> T handleGrpcError(StatusRuntimeException e, String operation) {
    Status.Code code = e.getStatus().getCode();
    
    switch (code) {
        case UNAVAILABLE:
            throw ServiceUnavailableException.identityServiceUnavailable(); // 503
        case DEADLINE_EXCEEDED:
            throw GatewayTimeoutException.identityServiceTimeout(); // 504
        case NOT_FOUND:
            throw new ResourceNotFoundException("USER_NOT_FOUND", "User not found"); // 404
        case PERMISSION_DENIED:
            throw new ForbiddenException("FORBIDDEN", "Access denied"); // 403
        case INVALID_ARGUMENT:
            throw new BadRequestException("BAD_REQUEST", "Invalid request"); // 400
        default:
            throw new RuntimeException("Failed to " + operation + ": " + code);
    }
}
```

**Usage Pattern:**

```java
try {
    VerifyUserResponse verification = identityServiceClient.verifyUserExists(userId);
    // ... process response ...
    
} catch (StatusRuntimeException e) {
    log.error("gRPC call failed: operation={}, status={}", "verify user", e.getStatus());
    return handleGrpcError(e, "verify user");
}
```

**Exception Classes:**

| Exception | HTTP Status | Use Case |
|-----------|-------------|----------|
| ServiceUnavailableException | 503 | Identity Service down/unreachable |
| GatewayTimeoutException | 504 | gRPC call exceeds 5s deadline |
| ResourceNotFoundException | 404 | User/Lecturer not found |
| ForbiddenException | 403 | Permission denied from Identity Service |
| BadRequestException | 400 | Invalid argument to gRPC call |

**GlobalExceptionHandler Mappings:**

```java
@ExceptionHandler(ServiceUnavailableException.class)
public ResponseEntity<ErrorResponse> handleServiceUnavailable(ServiceUnavailableException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(...);
}

@ExceptionHandler(GatewayTimeoutException.class)
public ResponseEntity<ErrorResponse> handleGatewayTimeout(GatewayTimeoutException ex) {
    return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(...);
}
```

**Client Retry Guidance:**

- **503 SERVICE_UNAVAILABLE:** Retry with exponential backoff (temporary outage)
- **504 GATEWAY_TIMEOUT:** Retry once (may be transient network issue)
- **404 NOT_FOUND:** Do not retry (resource doesn't exist)
- **403 FORBIDDEN:** Do not retry (authorization issue)
- **400 BAD_REQUEST:** Do not retry (client error)
```

**Type:** IMPLEMENTATION DOCUMENTATION

---

## FILE 4: 05_IMPLEMENTATION_CONFIG.md

### Update 4.1: UC21 Fallback Behavior

**Section:** Add to UC21 implementation section

**Location:** After existing UC21 authorization logic

**Content to Add:**

```markdown
#### UC21: LECTURER Authorization - gRPC Fallback Behavior

**Design Decision:** Availability > Strict Security during outages

**Implementation:**

```java
if (isLecturer) {
    try {
        // Attempt to verify target user role
        GetUserRoleResponse roleResponse = identityServiceClient.getUserRole(targetUserId);
        
        if (roleResponse.getRole() == UserRole.STUDENT) {
            return; // ALLOW - target is STUDENT
        } else {
            throw ForbiddenException.lecturerCanOnlyViewStudents(); // 403
        }
        
    } catch (StatusRuntimeException e) {
        // gRPC failure → FALLBACK BEHAVIOR
        log.warn("LECTURER {} viewing user {} - gRPC failed ({}). Allowing per spec (fallback).", 
                actorId, targetUserId, e.getStatus().getCode());
        return; // ALLOW with warning
    }
}
```

**Behavior Matrix:**

| Scenario | gRPC Response | Target Role | Action | HTTP Status |
|----------|---------------|-------------|--------|-------------|
| Normal | SUCCESS | STUDENT | Allow | 200 OK |
| Normal | SUCCESS | LECTURER | Deny | 403 FORBIDDEN |
| Normal | SUCCESS | ADMIN | Deny | 403 FORBIDDEN |
| Outage | UNAVAILABLE | Unknown | Allow + log warning | 200 OK |
| Outage | DEADLINE_EXCEEDED | Unknown | Allow + log warning | 200 OK |

**Rationale:**

1. **Availability Priority:** During Identity Service outage, LECTURER should still access system
2. **Graceful Degradation:** Temporary relaxation of strict security better than complete denial
3. **Audit Trail:** Warning logs track all fallback cases for security review
4. **Risk Mitigation:** LECTURER accessing non-STUDENT during outage is low risk vs. system unavailability

**Trade-offs:**
- ✅ System remains available during Identity Service outage
- ✅ LECTURER can continue work (view students)
- ⚠️ LECTURER might access non-STUDENT user during outage (logged)
- ⚠️ Security slightly relaxed during outage

**Alternatives Considered:**
- **Strict deny on gRPC failure:** Rejected - breaks system during outage
- **Cache user roles:** Rejected - stale data risk, cache invalidation complexity
- **Fallback to secondary Identity Service:** Future enhancement

**Monitoring:**

Watch for warning logs indicating fallback usage:
```
WARN: LECTURER <uuid> viewing user <uuid> - gRPC failed (UNAVAILABLE). Allowing per spec (fallback).
```

High frequency suggests Identity Service stability issue.
```

**Type:** DESIGN RATIONALE + CLARIFICATION

---

## FILE 5: 01_REQUIREMENTS.md

### Update 5.1: UC26 Business Rule Clarification

**Section:** Existing UC26 section

**Location:** After UC26 rules

**Content to Add:**

```markdown
#### Validation Detail

**Remove LEADER validation:**

System counts users with role=MEMBER (excludes LEADER being removed):

```java
long memberCount = countMembersByGroupId(groupId); // Counts MEMBER role only
if (memberCount > 0) {
    throw CANNOT_REMOVE_LEADER;
}
```

**Examples:**

| Group State | Remove Operation | Result |
|-------------|------------------|--------|
| 1 LEADER + 0 MEMBER | Remove LEADER | 204 SUCCESS |
| 1 LEADER + 1 MEMBER | Remove LEADER | 409 CANNOT_REMOVE_LEADER |
| 1 LEADER + 2 MEMBER | Remove LEADER | 409 CANNOT_REMOVE_LEADER |
| 1 LEADER + 1 MEMBER | Remove MEMBER | 204 SUCCESS |

**Business Logic:** LEADER can only be removed from empty group (no MEMBER role users).
```

**Type:** CLARIFICATION

---

### Update 5.2: Delete Group Behavior

**Section:** Add new section after UC26 or in "Additional Operations"

**Location:** After all use cases

**Content to Add:**

```markdown
### Delete Group (ADMIN)

**Endpoint:** `DELETE /api/groups/{groupId}`

**Actor:** ADMIN

**Behavior:**

System performs cascade soft delete:
1. Soft delete group (set deleted_at)
2. Soft delete all memberships in group (cascade)

**Business Rule:**
- Allowed regardless of member count
- All members automatically soft deleted
- Historical data preserved (soft delete)

**Examples:**

| Group State | Delete Operation | Result |
|-------------|------------------|--------|
| Empty group | Delete | Group + 0 memberships soft deleted |
| Group with 5 members | Delete | Group + 5 memberships soft deleted |

**Note:** Students will see group as deleted in their group list. No orphaned memberships remain.

**Alternative Approach (Not Implemented):**

Some systems prevent delete if group has members:
```java
if (memberCount > 0) {
    throw CANNOT_DELETE_GROUP_WITH_MEMBERS;
}
```

**Current Decision:** Allow cascade delete for operational flexibility.

**Trade-offs:**
- ✅ Easier for admins (one operation vs. remove all members first)
- ⚠️ Risk of accidental deletion (mitigated by soft delete)

**Error Codes:**
- `404 GROUP_NOT_FOUND`
- `401 UNAUTHORIZED`
- `403 FORBIDDEN` (not ADMIN)
```

**Type:** CLARIFICATION (existing behavior)

---

## SUMMARY OF CHANGES

| File | Sections Added/Updated | Type | Priority |
|------|------------------------|------|----------|
| 02_DESIGN.md | 3 sections | Clarification + Rationale | HIGH |
| 03_API_SPECIFICATION.md | 2 sections | Implementation Reference | HIGH |
| 04_VALIDATION_EXCEPTIONS.md | 2 sections | Clarification + Recommendation | MEDIUM |
| 05_IMPLEMENTATION_CONFIG.md | 1 section | Design Rationale | HIGH |
| 01_REQUIREMENTS.md | 2 sections | Clarification | MEDIUM |

**Total Updates:** 10 sections across 5 files

---

## IMPLEMENTATION NOTES

### What This Update Does:
- ✅ Documents actual implementation behavior
- ✅ Clarifies ambiguous design decisions
- ✅ Adds rationale for key implementation choices
- ✅ Marks recommendations as optional

### What This Update Does NOT Do:
- ❌ Change system behavior
- ❌ Add new requirements
- ❌ Rewrite existing correct documentation
- ❌ Mandate optional improvements

### Marking Convention:
- **"Current Implementation"** = What code does now (source of truth)
- **"Recommendation (Optional)"** = Suggested improvements (not required)
- **"Decision Needed"** = Requires product owner input

---

## VALIDATION CHECKLIST

After applying updates:

- [ ] All documented behaviors match CODE_REVIEW_FINAL.md
- [ ] No new requirements introduced
- [ ] Recommendations clearly marked as optional
- [ ] Design rationale provided for key decisions
- [ ] Cross-references between docs remain consistent
- [ ] Implementation details accurate and complete

---

**Document Status:** READY FOR APPLICATION  
**Next Step:** Apply updates to respective documentation files  
**Review Required:** Technical review after update application
