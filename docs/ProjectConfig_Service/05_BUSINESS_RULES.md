# BUSINESS RULES – PROJECT CONFIG SERVICE

## 1. Configuration Ownership Rules

### BR-PC-001: Unique Config per Group
**Rule:** Mỗi group chỉ được có duy nhất 1 project config.

**Enforcement:**
- Database constraint: `UNIQUE(group_id)` trong bảng `project_configs`
- API: Trả về `409 CONFIG_ALREADY_EXISTS` khi tạo config cho group đã có config

**Rationale:** Từ SRS Section 6.3.4, mỗi group chỉ làm 1 project, nên chỉ cần 1 config.

**Example:**
```
Group SE1705-G1 đã có config → Không thể tạo config thứ 2
→ Phải delete hoặc update config hiện tại
```

---

### BR-PC-002: Group Must Exist
**Rule:** Không thể tạo config cho group không tồn tại.

**Enforcement:**
- Database constraint: `FOREIGN KEY (group_id) REFERENCES groups(group_id)`
- Service layer: Verify group existence trước khi create

**Validation:**
```java
if (!groupRepository.existsById(groupId)) {
    throw new GroupNotFoundException(groupId);
}
```

---

### BR-PC-003: Team Leader Ownership
**Rule:** Team Leader chỉ có quyền quản lý config của group mình.

**Enforcement:**
```java
// Check if user is leader of the group
UserGroup membership = userGroupRepository
    .findByUserIdAndGroupId(userId, groupId)
    .orElseThrow(() -> new ForbiddenException("Not a member of this group"));

if (!Role.TEAM_LEADER.equals(membership.getRole())) {
    throw new ForbiddenException("Only team leader can manage config");
}
```

**Exception:** ADMIN có quyền quản lý tất cả configs.

---

## 2. Token Security Rules

### BR-PC-004: Token Encryption Required
**Rule:** Jira API Token và GitHub Token phải được mã hóa trước khi lưu vào database.

**Encryption Algorithm:** AES-256-GCM (theo SRS Section 4.5.3)

**Implementation:**
```java
public String encrypt(String plainToken) {
    // Generate IV (Initialization Vector) for each token
    byte[] iv = generateRandomIV();
    
    // Encrypt using AES-256-GCM
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
    byte[] encryptedBytes = cipher.doFinal(plainToken.getBytes());
    
    // Concatenate IV + encrypted data
    byte[] combined = concatenate(iv, encryptedBytes);
    
    // Encode to Base64 for storage
    return Base64.getEncoder().encodeToString(combined);
}
```

**Storage Format:**
```
Database value = Base64(IV + EncryptedToken)
```

---

### BR-PC-005: Token Masking in Responses
**Rule:** API responses phải mask tokens, chỉ hiển thị một số ký tự đầu.

**Jira Token Masking:**
- Format: `ATATT[site][random]`
- Hiển thị: 7 ký tự đầu + `***...`
- Example: `ATATT3x***...`

**GitHub Token Masking:**
- Format: `ghp_[40 characters]`
- Hiển thị: 4 ký tự đầu + `***...`
- Example: `ghp_***...`

**Implementation:**
```java
public String maskJiraToken(String token) {
    if (token == null || token.length() < 7) return "***";
    return token.substring(0, 7) + "***...";
}

public String maskGitHubToken(String token) {
    if (token == null || token.length() < 4) return "***";
    return token.substring(0, 4) + "***...";
}
```

**Exception:** Internal endpoint `/internal/project-configs/{id}/tokens` trả về token đã decrypt (không mask).

---

### BR-PC-006: Token Rotation
**Rule:** Khi user update token, token cũ phải được ghi log (audit) trước khi thay thế.

**Audit Log Entry:**
```json
{
  "action": "TOKEN_ROTATED",
  "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "tokenType": "JIRA_API_TOKEN",
  "oldTokenPreview": "ATATT3x***...",
  "newTokenPreview": "ATATT5y***...",
  "rotatedBy": "user-uuid",
  "rotatedAt": "2026-01-29T15:00:00Z",
  "ipAddress": "192.168.1.100"
}
```

---

## 3. URL Validation Rules

### BR-PC-007: Jira Host URL Format
**Rule:** Jira host URL phải là domain Atlassian hợp lệ.

**Valid Formats:**
- `https://*.atlassian.net`
- Không cho phép HTTP (chỉ HTTPS)

**Examples:**
```
✅ https://yourproject.atlassian.net
✅ https://company-team.atlassian.net
❌ http://yourproject.atlassian.net (HTTP not allowed)
❌ https://yourproject.com (Not atlassian.net domain)
❌ yourproject.atlassian.net (Missing protocol)
```

**Regex Pattern:**
```regex
^https://[a-zA-Z0-9-]+\.atlassian\.net$
```

---

### BR-PC-008: GitHub Repository URL Format
**Rule:** GitHub repository URL phải là GitHub URL hợp lệ.

**Valid Formats:**
- `https://github.com/{owner}/{repo}`
- Không cho phép HTTP
- Không cho phép URLs có `.git` suffix

**Examples:**
```
✅ https://github.com/microsoft/vscode
✅ https://github.com/spring-projects/spring-boot
❌ http://github.com/owner/repo (HTTP not allowed)
❌ https://github.com/owner/repo.git (.git suffix not needed)
❌ github.com/owner/repo (Missing protocol)
❌ https://gitlab.com/owner/repo (Not GitHub)
```

**Regex Pattern:**
```regex
^https://github\.com/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+$
```

---

## 4. Verification Rules

### BR-PC-009: Connection Verification
**Rule:** Trước khi lưu config, hệ thống nên khuyến khích user verify connection.

**UI Flow:**
```
1. User nhập credentials
2. User click "Verify Connection" (optional)
3. System test connection với Jira & GitHub
4. Hiển thị kết quả verify
5. User click "Save" để lưu config
```

**Note:** Verify là optional, user có thể save mà không verify. Nhưng nếu verify failed, hệ thống sẽ warning.

---

### BR-PC-010: Verification Rate Limiting
**Rule:** Verify endpoint bị giới hạn rate để tránh spam.

**Limits:**
- **Per User:** 10 verify requests / minute
- **Reason:** Mỗi verify call gọi đến external APIs (Jira, GitHub), tốn tài nguyên

**Implementation:**
```java
@RateLimiter(name = "verify-config", fallbackMethod = "verifyRateLimitFallback")
public VerificationResult verify(VerifyRequest request) {
    // Verify logic
}

public VerificationResult verifyRateLimitFallback(Exception e) {
    throw new RateLimitExceededException("Too many verification requests. Try again later.");
}
```

---

### BR-PC-011: Verification Timeout
**Rule:** Mỗi connection verify phải có timeout để tránh blocking.

**Timeouts:**
- Jira API call: 10 seconds
- GitHub API call: 10 seconds
- Total timeout: 30 seconds (có thể retry)

**Behavior:**
```
If Jira timeout → Status = TIMEOUT, continue check GitHub
If GitHub timeout → Status = TIMEOUT
If both timeout → Overall status = FAILED
```

---

## 5. Soft Delete Rules

### BR-PC-012: Soft Delete Pattern
**Rule:** Config không được xóa vật lý, chỉ soft delete.

**Implementation:**
```sql
ALTER TABLE project_configs ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE project_configs ADD COLUMN deleted_by UUID NULL;
```

**DELETE Operation:**
```java
public void deleteConfig(UUID configId, UUID deletedBy) {
    ProjectConfig config = findById(configId);
    config.setDeletedAt(LocalDateTime.now());
    config.setDeletedBy(deletedBy);
    repository.save(config);
}
```

**Query Pattern:**
```java
// Default queries exclude deleted records
@Query("SELECT c FROM ProjectConfig c WHERE c.deletedAt IS NULL")
List<ProjectConfig> findAll();

// Explicit query for deleted records
@Query("SELECT c FROM ProjectConfig c WHERE c.deletedAt IS NOT NULL")
List<ProjectConfig> findDeleted();
```

---

### BR-PC-013: Restore Deleted Config
**Rule:** Config đã xóa có thể restore trong vòng 30 ngày.

**Restore Endpoint:**
```
POST /api/project-configs/{configId}/restore
```

**Implementation:**
```java
public void restoreConfig(UUID configId, UUID restoredBy) {
    ProjectConfig config = findDeletedById(configId);
    
    // Check if deleted within 30 days
    if (config.getDeletedAt().isBefore(LocalDateTime.now().minusDays(30))) {
        throw new ConfigExpiredException("Config deleted more than 30 days ago");
    }
    
    config.setDeletedAt(null);
    config.setDeletedBy(null);
    repository.save(config);
    
    auditLog("CONFIG_RESTORED", configId, restoredBy);
}
```

---

### BR-PC-014: Permanent Deletion
**Rule:** Configs đã xóa quá 30 ngày sẽ bị xóa vĩnh viễn tự động.

**Cleanup Job:**
```java
@Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM daily
public void cleanupExpiredConfigs() {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
    
    List<ProjectConfig> expiredConfigs = repository
        .findByDeletedAtBefore(cutoff);
    
    for (ProjectConfig config : expiredConfigs) {
        auditLog("CONFIG_PERMANENTLY_DELETED", config.getConfigId(), "SYSTEM");
        repository.delete(config);
    }
    
    log.info("Deleted {} expired configs", expiredConfigs.size());
}
```

---

## 6. Data Integrity Rules

### BR-PC-015: Group Deletion Cascade

**Open Question:** Khi group bị xóa, config của group đó xử lý như thế nào?

**Options:**
1. **CASCADE DELETE:** Config cũng bị soft delete
2. **RESTRICT:** Không cho xóa group nếu còn config
3. **SET NULL:** Set `group_id = NULL` (không khả thi vì violate UNIQUE constraint)

**Recommended:** Option 1 (CASCADE DELETE)

**Implementation:**
```java
// In UserGroupService
public void deleteGroup(UUID groupId, UUID deletedBy) {
    // First soft delete associated config
    projectConfigService.deleteConfigByGroupId(groupId, deletedBy);
    
    // Then soft delete group
    group.setDeletedAt(LocalDateTime.now());
    group.setDeletedBy(deletedBy);
    groupRepository.save(group);
}
```

---

### BR-PC-016: Concurrent Update Prevention
**Rule:** Sử dụng optimistic locking để tránh concurrent update conflicts.

**Implementation:**
```java
@Entity
@Table(name = "project_configs")
public class ProjectConfig {
    
    @Id
    private UUID configId;
    
    @Version
    private Long version;
    
    // Other fields...
}
```

**Error Handling:**
```java
try {
    repository.save(config);
} catch (OptimisticLockException e) {
    throw new ConcurrentUpdateException(
        "Config was updated by another user. Please refresh and try again."
    );
}
```

---

## 7. Authorization Rules

### BR-PC-017: Role-Based Access Matrix

| Action | ADMIN | LECTURER | TEAM_LEADER | STUDENT |
|--------|-------|----------|-------------|---------|
| Create Config | ✅ All groups | ❌ | ✅ Own group | ❌ |
| View Config | ✅ All | ✅ Supervised | ✅ Own group | ❌ |
| Update Config | ✅ All | ❌ | ✅ Own group | ❌ |
| Delete Config | ✅ All | ❌ | ✅ Own group | ❌ |
| Verify Config | ✅ All | ✅ Supervised | ✅ Own group | ❌ |
| Internal Token Access | ✅ Services | ❌ | ❌ | ❌ |

**Supervised:** Lecturer chỉ xem config của groups mà họ hướng dẫn (theo section 3.2.2 SRS).

---

### BR-PC-018: Service-to-Service Authentication
**Rule:** Internal endpoint `/internal/project-configs/{id}/tokens` chỉ accessible từ authenticated services.

**Authentication Method:**
- Header: `X-Internal-Service-Key: [api-key]`
- Header: `X-Service-Name: [service-name]`

**Whitelist Services:**
- `sync-service` (UC10: cần tokens để sync Jira/GitHub)
- `analysis-service` (có thể cần tokens trong tương lai)

**Implementation:**
```java
@PreAuthorize("hasAuthority('SERVICE')")
@GetMapping("/internal/project-configs/{configId}/tokens")
public DecryptedTokenResponse getDecryptedTokens(
    @PathVariable UUID configId,
    @RequestHeader("X-Service-Name") String serviceName
) {
    validateServiceAccess(serviceName);
    return projectConfigService.getDecryptedTokens(configId);
}
```

---

## 8. Audit Logging Rules

### BR-PC-019: Mandatory Audit Events
**Rule:** Các actions sau phải được audit log:

| Event | Description |
|-------|-------------|
| CONFIG_CREATED | Config được tạo |
| CONFIG_UPDATED | Config được update (log changes) |
| CONFIG_DELETED | Config bị soft delete |
| CONFIG_RESTORED | Config bị restore |
| TOKEN_ROTATED | Token được thay đổi |
| TOKEN_DECRYPTED | Internal service decrypt token |
| VERIFY_CONNECTION | User verify connection |
| UNAUTHORIZED_ACCESS | Attempt truy cập config không có quyền |

**Audit Log Schema:**
```json
{
  "eventId": "uuid",
  "eventType": "CONFIG_UPDATED",
  "configId": "uuid",
  "userId": "uuid",
  "ipAddress": "192.168.1.100",
  "userAgent": "Mozilla/5.0...",
  "timestamp": "2026-01-29T15:00:00Z",
  "changes": {
    "jiraHostUrl": {
      "from": "https://old.atlassian.net",
      "to": "https://new.atlassian.net"
    }
  },
  "success": true
}
```

---

### BR-PC-020: Audit Log Retention
**Rule:** Audit logs phải giữ ít nhất 1 năm cho compliance.

**Implementation:**
```java
@Scheduled(cron = "0 0 3 * * ?") // Run at 3 AM daily
public void archiveOldAuditLogs() {
    LocalDateTime cutoff = LocalDateTime.now().minusYears(1);
    
    // Move to archive table
    auditLogRepository.archiveOldLogs(cutoff);
    
    log.info("Archived audit logs older than {}", cutoff);
}
```

---

## 9. Token Format Rules

### BR-PC-021: Jira API Token Format
**Expected Format:** `ATATT[site][random]` (Atlassian API token format)

**Validation:**
```java
private static final Pattern JIRA_TOKEN_PATTERN = 
    Pattern.compile("^ATATT[a-zA-Z0-9]+$");

public void validateJiraToken(String token) {
    if (!JIRA_TOKEN_PATTERN.matcher(token).matches()) {
        throw new InvalidTokenFormatException(
            "Jira API token must start with 'ATATT'"
        );
    }
}
```

---

### BR-PC-022: GitHub Token Format
**Expected Format:** `ghp_[40 characters]` (GitHub Personal Access Token)

**Validation:**
```java
private static final Pattern GITHUB_TOKEN_PATTERN = 
    Pattern.compile("^ghp_[a-zA-Z0-9]{40}$");

public void validateGitHubToken(String token) {
    if (!GITHUB_TOKEN_PATTERN.matcher(token).matches()) {
        throw new InvalidTokenFormatException(
            "GitHub token must start with 'ghp_' followed by 40 characters"
        );
    }
}
```

**Note:** GitHub còn có các token types khác (ghc_, gho_, ghu_, ghs_, ghr_), nhưng chỉ support `ghp_` (classic personal access token).

---

## 10. Error Handling Rules

### BR-PC-023: Graceful Degradation
**Rule:** Nếu không decrypt được token (encryption key bị mất), vẫn phải trả về config nhưng không có tokens.

**Implementation:**
```java
public ProjectConfigResponse toResponse(ProjectConfig config) {
    try {
        String decryptedJiraToken = decrypt(config.getJiraApiToken());
        String decryptedGithubToken = decrypt(config.getGithubToken());
        
        return ProjectConfigResponse.builder()
            .configId(config.getConfigId())
            .jiraApiToken(maskJiraToken(decryptedJiraToken))
            .githubToken(maskGitHubToken(decryptedGithubToken))
            .build();
            
    } catch (DecryptionException e) {
        log.error("Failed to decrypt tokens for config {}", config.getConfigId(), e);
        
        // Return config without tokens
        return ProjectConfigResponse.builder()
            .configId(config.getConfigId())
            .jiraApiToken("***DECRYPTION_FAILED***")
            .githubToken("***DECRYPTION_FAILED***")
            .build();
    }
}
```

---

### BR-PC-024: Transaction Boundaries
**Rule:** Create/Update/Delete config phải wrap trong transaction.

**Implementation:**
```java
@Transactional(rollbackFor = Exception.class)
public ProjectConfig createConfig(CreateConfigRequest request, UUID createdBy) {
    // Validate group exists
    Group group = groupRepository.findById(request.getGroupId())
        .orElseThrow(() -> new GroupNotFoundException(request.getGroupId()));
    
    // Check if config already exists
    if (repository.existsByGroupId(request.getGroupId())) {
        throw new ConfigAlreadyExistsException(request.getGroupId());
    }
    
    // Encrypt tokens
    String encryptedJiraToken = encrypt(request.getJiraApiToken());
    String encryptedGithubToken = encrypt(request.getGithubToken());
    
    // Create config
    ProjectConfig config = ProjectConfig.builder()
        .configId(UUID.randomUUID())
        .groupId(request.getGroupId())
        .jiraHostUrl(request.getJiraHostUrl())
        .jiraApiToken(encryptedJiraToken)
        .githubRepoUrl(request.getGithubRepoUrl())
        .githubToken(encryptedGithubToken)
        .createdAt(LocalDateTime.now())
        .build();
    
    config = repository.save(config);
    
    // Audit log
    auditLog("CONFIG_CREATED", config.getConfigId(), createdBy);
    
    return config;
}
```

---

## Summary Table

| Rule ID | Rule Name | Category | Priority |
|---------|-----------|----------|----------|
| BR-PC-001 | Unique Config per Group | Ownership | HIGH |
| BR-PC-002 | Group Must Exist | Integrity | HIGH |
| BR-PC-003 | Team Leader Ownership | Authorization | HIGH |
| BR-PC-004 | Token Encryption Required | Security | CRITICAL |
| BR-PC-005 | Token Masking in Responses | Security | HIGH |
| BR-PC-006 | Token Rotation | Security | MEDIUM |
| BR-PC-007 | Jira Host URL Format | Validation | HIGH |
| BR-PC-008 | GitHub Repository URL Format | Validation | HIGH |
| BR-PC-009 | Connection Verification | UX | LOW |
| BR-PC-010 | Verification Rate Limiting | Performance | MEDIUM |
| BR-PC-011 | Verification Timeout | Performance | MEDIUM |
| BR-PC-012 | Soft Delete Pattern | Integrity | HIGH |
| BR-PC-013 | Restore Deleted Config | UX | MEDIUM |
| BR-PC-014 | Permanent Deletion | Maintenance | MEDIUM |
| BR-PC-015 | Group Deletion Cascade | Integrity | HIGH |
| BR-PC-016 | Concurrent Update Prevention | Integrity | HIGH |
| BR-PC-017 | Role-Based Access Matrix | Authorization | CRITICAL |
| BR-PC-018 | Service-to-Service Auth | Security | CRITICAL |
| BR-PC-019 | Mandatory Audit Events | Compliance | HIGH |
| BR-PC-020 | Audit Log Retention | Compliance | HIGH |
| BR-PC-021 | Jira API Token Format | Validation | MEDIUM |
| BR-PC-022 | GitHub Token Format | Validation | MEDIUM |
| BR-PC-023 | Graceful Degradation | Reliability | MEDIUM |
| BR-PC-024 | Transaction Boundaries | Integrity | HIGH |
