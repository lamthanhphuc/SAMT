# Soft Delete Retention Policy

**Version:** 1.0  
**Date:** January 30, 2026  
**Applies to:** All services (Identity, UserGroup, ProjectConfig, Sync, AI, Reporting)

---

## 1. Overview

Tất cả entities trong hệ thống SAMT sử dụng **soft delete** để đảm bảo data integrity, audit trail và khả năng recovery. Document này định nghĩa retention policy và quy trình hard delete.

---

## 2. Soft Delete Mechanism

### 2.1 Database Schema

Tất cả entities có 2 columns:

```sql
deleted_at TIMESTAMP NULL,        -- NULL = active, NOT NULL = soft deleted
deleted_by UUID NULL REFERENCES users(id)  -- Admin who performed deletion
```

### 2.2 Query Behavior

**Default queries (Hibernate):**

```java
@SQLRestriction("deleted_at IS NULL")
public class User { ... }
```

- Tất cả query tự động filter `deleted_at IS NULL`
- Deleted entities INVISIBLE đối với business logic

**Admin queries:**

```java
@Query(value = "SELECT * FROM users WHERE deleted_at IS NOT NULL", nativeQuery = true)
List<User> findDeletedUsers();
```

- Admin có thể query deleted entities qua native SQL
- Use case: Restore, audit, analytics

---

## 3. Retention Policy

### 3.1 Retention Period

**Policy:** **90 days**

| Entity | Soft Delete Duration | Hard Delete After | Responsible Service |
|--------|----------------------|-------------------|---------------------|
| Users | 90 days | Day 91 | Identity Service |
| Groups | 90 days | Day 91 | UserGroup Service |
| User_Groups | 90 days (cascade with Group) | Day 91 | UserGroup Service |
| Project_Configs | 90 days | Day 91 | ProjectConfig Service |
| Jira_Issues | 90 days (cascade with Group) | Day 91 | Sync Service |
| Github_Commits | 90 days (cascade with Group) | Day 91 | Sync Service |
| Reports | 90 days (cascade with Group) | Day 91 | Reporting Service |

**Rationale:**
- **Data recovery:** 90 days đủ thời gian để phát hiện và khôi phục lỗi xóa nhầm
- **Compliance:** Đáp ứng GDPR right to erasure (30 days grace period, 90 days tổng cộng)
- **Storage optimization:** Tránh lưu trữ vĩnh viễn deleted data

---

### 3.2 Grace Period Calculation

```
Soft Delete Date: 2026-01-30
Grace Period: 90 days
Eligible for Hard Delete: 2026-04-30 (inclusive)
```

**Query để tìm entities hết grace period:**

```sql
SELECT * FROM users
WHERE deleted_at IS NOT NULL
  AND deleted_at < NOW() - INTERVAL '90 days';
```

---

## 4. Hard Delete Process

### 4.1 Manual Hard Delete (Super Admin)

#### UC-HARD-DELETE-USER (Identity Service)

**Actor:** **Super Admin** (role = SUPER_ADMIN)

**Purpose:** Xóa vĩnh viễn user đã soft delete quá 90 days

**API Endpoint:** `DELETE /api/admin/users/{userId}/permanent`

**Query Parameter:** `force=true` (required để confirm)

**Preconditions:**
- Caller có role SUPER_ADMIN
- User đã soft delete ≥ 90 days
- User không có dependencies chưa xóa (orphan check)

**Main Flow:**
1. Super Admin gọi API với `force=true`
2. System verify SUPER_ADMIN role
3. System check `deleted_at` ≥ 90 days ago
4. System check cascading dependencies (refresh_tokens, audit_logs)
5. System **PERMANENTLY DELETE** từ database
6. System ghi audit log (CRITICAL: audit log KHÔNG bị xóa)
7. System trả về confirmation

**Business Rules:**
- BR-HD-01: Chỉ SUPER_ADMIN có quyền hard delete
- BR-HD-02: User phải soft delete ≥ 90 days
- BR-HD-03: Audit log của user KHÔNG bị xóa (permanent record)
- BR-HD-04: Cascading delete: refresh_tokens (ON DELETE CASCADE)
- BR-HD-05: External references: jira_issues.assignee_user_id, github_commits.author_user_id → SET NULL

**Error Codes:**
- 401 UNAUTHORIZED: Not authenticated
- 403 FORBIDDEN: Not SUPER_ADMIN role
- 404 USER_NOT_FOUND: User doesn't exist
- 400 BAD REQUEST: User not soft deleted
- 409 CONFLICT: Grace period not elapsed (<90 days)
- 400 BAD REQUEST: Missing `force=true` parameter (safety check)

**Audit Log:**

```json
{
  "action": "HARD_DELETE_USER",
  "entityType": "User",
  "entityId": "user-uuid",
  "outcome": "SUCCESS",
  "actorId": "super-admin-uuid",
  "metadata": {
    "softDeletedAt": "2026-01-30T10:00:00Z",
    "gracePeriodDays": 90,
    "deletedEmail": "student@university.edu",  // Preserved for audit
    "deletedRole": "STUDENT"
  },
  "timestamp": "2026-04-30T15:00:00Z",
  "serviceName": "IdentityService"
}
```

---

#### UC-HARD-DELETE-GROUP (UserGroup Service)

**Actor:** **Super Admin**

**API Endpoint:** `DELETE /api/admin/groups/{groupId}/permanent?force=true`

**Cascading Behavior:**
- Hard delete `user_groups` (membership records)
- Hard delete `project_configs` (associated configs)
- Update `jira_issues` và `github_commits`: SET group_id = NULL
- Hard delete `reports` (generated reports)

**Business Rules:**
- BR-HD-06: Group phải soft delete ≥ 90 days
- BR-HD-07: Cascade hard delete related entities
- BR-HD-08: Audit logs KHÔNG bị xóa

---

#### UC-HARD-DELETE-CONFIG (ProjectConfig Service)

**Actor:** **Super Admin**

**API Endpoint:** `DELETE /api/admin/project-configs/{configId}/permanent?force=true`

**Business Rules:**
- BR-HD-09: Config phải soft delete ≥ 90 days
- BR-HD-10: Encrypted tokens bị xóa vĩnh viễn (không thể khôi phục)

---

### 4.2 Automated Hard Delete (Scheduled Job)

#### Job: `HardDeleteCleanupJob`

**Schedule:** Chạy hằng ngày lúc 02:00 AM (UTC)

**Algorithm:**

```java
@Scheduled(cron = "0 0 2 * * *")  // 02:00 AM daily
public void runHardDeleteCleanup() {
    LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
    
    // Step 1: Find eligible users
    List<User> eligibleUsers = userRepository.findDeletedUsersOlderThan(cutoffDate);
    
    // Step 2: Hard delete each user
    for (User user : eligibleUsers) {
        try {
            hardDeleteService.hardDeleteUser(user.getId());
            log.info("Hard deleted user: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to hard delete user: {}", user.getId(), e);
            // Continue with next user (don't fail entire job)
        }
    }
    
    // Step 3: Repeat for Groups, Configs, etc.
    hardDeleteService.hardDeleteEligibleGroups(cutoffDate);
    hardDeleteService.hardDeleteEligibleConfigs(cutoffDate);
    
    // Step 4: Send summary report to admins
    adminNotificationService.sendCleanupReport(
        eligibleUsers.size(),
        successCount,
        failureCount
    );
}
```

**Safety Features:**
- **Dry-run mode:** `application.yml` config to enable/disable hard delete
- **Batch size:** Process max 100 entities per run (prevent overload)
- **Error handling:** Failure on 1 entity doesn't fail entire job
- **Notification:** Email summary to admins after each run

**Configuration:**

```yaml
cleanup:
  hard-delete:
    enabled: true              # Set to false to disable automated cleanup
    retention-days: 90         # Grace period
    batch-size: 100            # Max entities per run
    dry-run: false             # Set to true for testing (log only, no delete)
```

---

## 5. Restore Capability

### 5.1 Restore Before Hard Delete

**Use Cases:**
- **UC-RESTORE (Identity Service):** Restore soft deleted user (before 90 days)
- **UC35: Restore Config (ProjectConfig Service):** Restore soft deleted config

**API Endpoint:** `POST /api/admin/users/{userId}/restore`

**Effect:**
- Set `deleted_at = NULL`
- Set `deleted_by = NULL`
- Entity becomes visible again
- Audit log: `RESTORE_USER`

**Restriction:** ONLY possible if `deleted_at + 90 days > NOW()`

---

### 5.2 No Restore After Hard Delete

**CRITICAL:** Once hard deleted, data is **PERMANENTLY LOST**. No recovery possible.

**Only remaining trace:**
- Audit logs (preserved forever)
- Backup snapshots (if database backups enabled)

---

## 6. Cross-Service Coordination

### 6.1 Hard Delete Order

To maintain referential integrity, hard delete must follow this order:

```
1. Jira_Issues, Github_Commits (SET group_id = NULL, assignee_user_id = NULL)
2. Reports (DELETE)
3. Project_Configs (DELETE)
4. User_Groups (DELETE)
5. Groups (DELETE)
6. Refresh_Tokens (CASCADE DELETE via FK)
7. Users (DELETE)
```

**Implementation:**
- Each service exposes `/internal/hard-delete-cleanup` endpoint
- Identity Service (orchestrator) calls services in correct order
- Uses distributed transaction pattern (Saga) for atomicity

---

### 6.2 Service-to-Service API

**POST** `/internal/hard-delete/{entityType}/{entityId}`

**Headers:**
```
X-Service-Name: IdentityService
X-Service-Key: {service_key}
```

**Response:**
```json
{
  "success": true,
  "deletedCount": 1,
  "affectedEntities": ["refresh_tokens"]
}
```

---

## 7. Monitoring & Alerts

### 7.1 Metrics

- **Soft deletes per day:** Track deletion rate
- **Entities eligible for hard delete:** Count entities past grace period
- **Hard delete success rate:** % successful hard deletes
- **Grace period violations:** Entities deleted before 90 days (should be 0)

### 7.2 Alerts

- **High deletion rate:** Alert if >10% users soft deleted in 24h (potential data leak)
- **Hard delete failure:** Alert Super Admin if hard delete job fails
- **Grace period violation:** Alert if system attempts hard delete <90 days

---

## 8. Compliance & Legal

### 8.1 GDPR Right to Erasure

**Request:** User requests permanent data deletion

**Process:**
1. User submits erasure request
2. Admin soft deletes user immediately
3. After 90 days, automated job hard deletes user
4. Audit log preserved (legal requirement)

**Timeline:**
- Request date: Day 0
- Soft delete: Day 0
- Hard delete: Day 90 (automatic)
- Total compliance time: 90 days

---

### 8.2 Data Retention for Audit

**Exception to deletion policy:**

| Entity Type | Retention | Reason |
|-------------|-----------|--------|
| Audit Logs | **PERMANENT** | Legal compliance, security investigation |
| Backup Snapshots | 1 year | Disaster recovery |

**CRITICAL:** Audit logs are NEVER deleted, even after hard delete of entities.

---

## 9. Testing & Validation

### 9.1 Test Scenarios

1. **Soft delete user → Wait 90 days → Auto hard delete**
   - Verify audit log preserved
   - Verify external references SET NULL

2. **Soft delete user → Restore before 90 days → User active**
   - Verify `deleted_at = NULL`

3. **Super Admin manual hard delete (force=true)**
   - Verify grace period check
   - Verify cascading behavior

4. **Attempt hard delete <90 days → 409 CONFLICT**
   - Verify grace period enforced

---

## 10. Implementation Checklist

### Identity Service
- ✅ Add `deleted_at`, `deleted_by` to users table
- ✅ Implement UC-SOFT-DELETE
- ✅ Implement UC-RESTORE
- ⏳ Implement UC-HARD-DELETE-USER (Super Admin)
- ⏳ Create HardDeleteCleanupJob (scheduled)

### UserGroup Service
- ✅ Add `deleted_at` to groups, user_groups tables
- ⏳ Implement UC-HARD-DELETE-GROUP (Super Admin)
- ⏳ Implement cascading hard delete logic

### ProjectConfig Service
- ✅ Add `deleted_at`, `deleted_by` to project_configs table
- ✅ Implement UC33: Delete (soft delete)
- ⏳ Implement UC35: Restore
- ⏳ Implement UC-HARD-DELETE-CONFIG (Super Admin)

### All Services
- ⏳ Add `hard-delete.enabled` config flag
- ⏳ Implement `/internal/hard-delete-cleanup` endpoint
- ⏳ Integrate with Audit Service (centralized logging)

---

## ✅ Status

**⏳ PLANNED** – Policy defined, implementation in progress

**Target Implementation:** Q2 2026 (after Audit Service deployment)
