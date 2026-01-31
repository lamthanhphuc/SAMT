# TODO - User & Group Service Fixes

**Priority:** CRITICAL FIXES FIRST  
**Deadline:** Before Production Deployment  
**Reference:** [CODE_REVIEW.md](CODE_REVIEW.md)

---

## 🚨 CRITICAL - MUST FIX (Estimate: 10 hours)

### 1. Implement UC27 - Update Group Lecturer ⏱️ 4h

**Issue:** Complete use case missing from implementation  
**Spec Reference:** Requirements.md UC27, API Spec line 271-295  
**Files to Create/Modify:**
- `UpdateLecturerRequest.java` (DTO)
- `GroupController.java` - Add `PATCH /groups/{groupId}/lecturer`
- `GroupService.java` - Add `updateGroupLecturer()` method
- `GroupServiceImpl.java` - Implement method with gRPC validation

**Implementation Checklist:**
- [ ] Create `UpdateLecturerRequest` DTO with validation
- [ ] Add controller endpoint with `@PreAuthorize("hasRole('ADMIN')")`
- [ ] Implement service method:
  - [ ] Verify group exists
  - [ ] Verify new lecturer exists via gRPC
  - [ ] Verify new lecturer has LECTURER role via gRPC
  - [ ] Update group.lecturerId
  - [ ] Add audit logging (old value, new value)
- [ ] Return updated GroupResponse

**Code Template:**
```java
// UpdateLecturerRequest.java
@Data
public class UpdateLecturerRequest {
    @NotNull(message = "Lecturer ID is required")
    private UUID lecturerId;
}

// GroupController.java
@PatchMapping("/{groupId}/lecturer")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<GroupResponse> updateGroupLecturer(
        @PathVariable UUID groupId,
        @Valid @RequestBody UpdateLecturerRequest request) {
    GroupResponse response = groupService.updateGroupLecturer(groupId, request);
    return ResponseEntity.ok(response);
}

// GroupServiceImpl.java
@Transactional
public GroupResponse updateGroupLecturer(UUID groupId, UpdateLecturerRequest request) {
    // 1. Find group
    // 2. Verify new lecturer via gRPC (exists + active + LECTURER role)
    // 3. Log old lecturerId
    // 4. Update group.lecturerId
    // 5. Save and return response
}
```

---

### 2. Fix UC22 - updateUser() to Proxy Identity Service ⏱️ 3h

**Issue:** Current code throws UnsupportedOperationException, spec requires working implementation  
**Spec Reference:** Requirements.md UC22, API Spec line 76-104  
**Files to Modify:**
- `user_service.proto` - Add UpdateUser RPC
- `IdentityServiceClient.java` - Add updateUser() method
- `UserServiceImpl.java` - Replace exception with gRPC proxy call

**Implementation Checklist:**
- [ ] Add to proto file:
  ```protobuf
  rpc UpdateUser(UpdateUserRequest) returns (UpdateUserResponse);
  
  message UpdateUserRequest {
    string user_id = 1;
    string full_name = 2;
  }
  
  message UpdateUserResponse {
    GetUserResponse user = 1;
  }
  ```
- [ ] Add to `IdentityServiceClient.java`:
  ```java
  public UpdateUserResponse updateUser(String userId, String fullName) {
      UpdateUserRequest request = UpdateUserRequest.newBuilder()
              .setUserId(userId)
              .setFullName(fullName)
              .build();
      return blockingStub.updateUser(request);
  }
  ```
- [ ] Fix `UserServiceImpl.updateUser()`:
  - [ ] Keep authorization checks (LECTURER excluded, ADMIN/SELF only)
  - [ ] Remove `throw UnsupportedOperationException`
  - [ ] Call `identityServiceClient.updateUser()`
  - [ ] Handle gRPC errors (NOT_FOUND → 404, etc.)
  - [ ] Return `UserResponse.fromGrpc()`

**Test:**
- [ ] ADMIN updates any user → 200 OK
- [ ] STUDENT updates self → 200 OK
- [ ] STUDENT updates other → 403 FORBIDDEN
- [ ] LECTURER updates any → 403 FORBIDDEN (even self)

---

### 3. Fix UC21 - LECTURER Authorization Logic ⏱️ 2h

**Issue:** Current code ALWAYS allows LECTURER to view any user with warning log. Spec requires check role = STUDENT.  
**Spec Reference:** Requirements.md UC21-AUTH clarification, API Spec line 32-36  
**Files to Modify:**
- `UserServiceImpl.java:211-221` (checkGetUserAuthorization method)

**Implementation Checklist:**
- [ ] Replace current LECTURER logic:
  ```java
  // OLD (WRONG):
  if (isLecturer) {
      log.warn("... Allowing per spec.");
      return; // ALWAYS ALLOW
  }
  
  // NEW (CORRECT):
  if (isLecturer) {
      try {
          GetUserRoleResponse roleResponse = identityServiceClient.getUserRole(targetUserId);
          
          if (roleResponse.getRole() != UserRole.STUDENT) {
              log.warn("LECTURER {} attempted to view non-STUDENT user {}", actorId, targetUserId);
              throw ForbiddenException.lecturerCanOnlyViewStudents();
          }
          
          return; // ALLOW - target is STUDENT
          
      } catch (StatusRuntimeException e) {
          // gRPC failed - cannot verify role
          log.warn("LECTURER {} viewing user {} - cannot verify role (gRPC error). Allowing per spec.", 
                  actorId, targetUserId);
          return; // Fallback: ALLOW with warning
      }
  }
  ```
- [ ] Add exception factory method:
  ```java
  // ForbiddenException.java
  public static ForbiddenException lecturerCanOnlyViewStudents() {
      return new ForbiddenException("FORBIDDEN", "Lecturers can only view student profiles");
  }
  ```

**Test:**
- [ ] LECTURER views STUDENT → 200 OK
- [ ] LECTURER views LECTURER → 403 FORBIDDEN
- [ ] LECTURER views ADMIN → 403 FORBIDDEN
- [ ] LECTURER views user (gRPC down) → 200 OK + warning log

---

### 4. Add STUDENT Role Validation in UC24 ⏱️ 1h

**Issue:** Missing business rule - only STUDENT can join groups  
**Spec Reference:** Design.md line 316-323  
**Files to Modify:**
- `GroupMemberServiceImpl.java:48-73` (addMember method)

**Implementation Checklist:**
- [ ] Add after user exists/active validation:
  ```java
  // Verify user has STUDENT role
  try {
      GetUserRoleResponse roleResponse = identityServiceClient.getUserRole(request.getUserId());
      
      if (roleResponse.getRole() != UserRole.STUDENT) {
          throw new ConflictException("INVALID_ROLE", 
                  "Only students can be added to groups. User role: " + roleResponse.getRole());
      }
  } catch (StatusRuntimeException e) {
      log.error("gRPC call failed when getting user role: {}", e.getStatus());
      throw new RuntimeException("Failed to verify user role: " + e.getStatus().getCode());
  }
  ```

**Test:**
- [ ] Add STUDENT to group → 200 CREATED
- [ ] Add LECTURER to group → 409 CONFLICT
- [ ] Add ADMIN to group → 409 CONFLICT

---

## ⚠️ HIGH PRIORITY - SHOULD FIX (Estimate: 8 hours)

### 5. Improve UC25 Transaction Isolation ⏱️ 3h

**Issue:** Race condition possible when multiple requests assign LEADER concurrently  
**Spec Reference:** Requirements.md UC25-LOCK  
**Files to Modify:**
- `GroupMemberServiceImpl.java:113-143` (assignRole method)

**Options:**
1. **Application-level lock** (synchronized block)
2. **Database advisory lock** (PostgreSQL pg_advisory_xact_lock)
3. **SERIALIZABLE isolation level**

**Recommended: Application Lock + Defensive Check**
```java
@Transactional(rollbackFor = Exception.class)
public MemberResponse assignRole(UUID groupId, UUID userId, AssignRoleRequest request) {
    // ... validation ...
    
    if (newRole == GroupRole.LEADER) {
        synchronized (("group_leader_" + groupId).intern()) {
            // Lock scope
            Optional<UserGroup> oldLeader = userGroupRepository
                    .findLeaderByGroupIdWithLock(groupId);
            
            if (oldLeader.isPresent() && !oldLeader.get().getUserId().equals(userId)) {
                oldLeader.get().setRole(GroupRole.MEMBER);
                userGroupRepository.saveAndFlush(oldLeader.get());
            }
            
            // Defensive check
            if (userGroupRepository.existsByGroupIdAndRole(groupId, GroupRole.LEADER)) {
                throw new ConflictException("LEADER_ALREADY_EXISTS", 
                        "Race condition detected");
            }
            
            membership.setRole(GroupRole.LEADER);
            userGroupRepository.saveAndFlush(membership);
        }
    }
    // ...
}
```

**Test:**
- [ ] Single request → Works
- [ ] 2 concurrent requests (same user) → 1 succeeds
- [ ] 2 concurrent requests (different users) → 1 succeeds, 1 fails

---

### 6. Fix UC26 removeMember Logic ⏱️ 1h

**Issue:** Logic unclear - should explicitly check "other members" not including leader being removed  
**Files to Modify:**
- `GroupMemberServiceImpl.java:182-187`
- `UserGroupRepository.java` - Add method

**Implementation:**
```java
// Repository
long countByGroupIdAndRoleAndUserIdNot(UUID groupId, GroupRole role, UUID excludeUserId);

// Service
if (membership.isLeader()) {
    long otherMembersCount = userGroupRepository.countByGroupIdAndRoleAndUserIdNot(
            groupId, GroupRole.MEMBER, userId);
    
    if (otherMembersCount > 0) {
        throw ConflictException.cannotRemoveLeader(
                "Cannot remove leader while group has " + otherMembersCount + " members");
    }
}
```

---

### 7. Fix HTTP Status Code Mapping ⏱️ 2h

**Issue:** gRPC errors throw generic RuntimeException (500) instead of proper HTTP status  
**Files to Modify:**
- All services with gRPC calls
- Create new exception classes

**Action Items:**
- [ ] Create `ServiceUnavailableException` (503)
- [ ] Create `GatewayTimeoutException` (504)
- [ ] Update all `catch (StatusRuntimeException e)` blocks:
  ```java
  if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
      throw new ServiceUnavailableException("Identity Service unavailable");
  }
  if (e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
      throw new GatewayTimeoutException("Identity Service timeout");
  }
  ```

---

### 8. Resolve listUsers() Inconsistency ⏱️ 2h

**Issue:** Method throws UnsupportedOperationException but endpoint exists  
**Options:**
1. Remove endpoint completely (if not used by frontend)
2. Proxy to Identity Service via gRPC
3. Move to Identity Service

**Decision Needed:** Check with frontend team

---

## 💡 NICE TO HAVE (Estimate: 2 hours)

### 9. Code Cleanup - Use demoteToMember() ⏱️ 5min

**Files:** `GroupMemberServiceImpl.java:133`

**Change:**
```java
// OLD:
oldLeader.setRole(GroupRole.MEMBER);

// NEW:
oldLeader.demoteToMember(); // More semantic
```

---

### 10. Add Business Rule for deleteGroup ⏱️ 1h

**Options:**
1. Require empty group before delete
2. Allow cascade delete (current) but document clearly

**Recommendation:** Add check for empty group:
```java
long memberCount = userGroupRepository.countAllMembersByGroupId(groupId);
if (memberCount > 0) {
    throw new ConflictException("CANNOT_DELETE_GROUP_WITH_MEMBERS", 
            "Group has " + memberCount + " members");
}
```

---

### 11. Add Performance Indexes ⏱️ 30min

**Files:** Migration files

**Add:**
```sql
CREATE INDEX idx_user_groups_deleted_at ON user_groups(deleted_at) 
    WHERE deleted_at IS NULL;

CREATE INDEX idx_groups_deleted_at ON groups(deleted_at)
    WHERE deleted_at IS NULL;
```

---

### 12. Improve JWT Validation ⏱️ 30min

**Files:** `JwtService.java:60-67`

**Change:**
```java
public boolean isTokenValid(String token) {
    try {
        Claims claims = extractAllClaims(token);
        Date expiration = claims.getExpiration();
        
        if (expiration == null) {
            log.warn("JWT has no expiration");
            return false; // Reject instead of allow
        }
        
        return !expiration.before(new Date());
    } catch (JwtException e) {
        log.warn("Invalid JWT: {}", e.getMessage());
        return false;
    }
}
```

---

## 📊 PROGRESS TRACKING

### Critical Fixes (Must Complete)
- [ ] UC27 - Update Group Lecturer (4h)
- [ ] UC22 - Fix updateUser() (3h)
- [ ] UC21 - Fix LECTURER auth (2h)
- [ ] UC24 - Add STUDENT role check (1h)

**Total: 10h | Progress: 0/4**

### High Priority (Should Complete)
- [ ] UC25 - Transaction isolation (3h)
- [ ] UC26 - Fix removeMember (1h)
- [ ] HTTP status mapping (2h)
- [ ] Resolve listUsers() (2h)

**Total: 8h | Progress: 0/4**

### Nice to Have (Optional)
- [ ] Code cleanup (5min)
- [ ] Business rule deleteGroup (1h)
- [ ] Performance indexes (30min)
- [ ] JWT validation (30min)

**Total: 2h | Progress: 0/4**

---

## ✅ COMPLETION CRITERIA

**Ready for Production When:**
1. ✅ All 4 critical fixes completed
2. ✅ All integration tests pass (UC21-UC27)
3. ✅ Code review approval
4. ✅ Security audit pass
5. ✅ Performance test pass (with Identity Service running)

**Next Steps:**
1. Prioritize critical fixes (items 1-4)
2. Create feature branch for fixes
3. Implement and test each fix
4. Request code review
5. Merge to main after approval

---

**Created:** January 31, 2026  
**Owner:** Development Team  
**Review Date:** After fixing critical issues
