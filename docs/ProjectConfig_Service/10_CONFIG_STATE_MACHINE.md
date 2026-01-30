# CONFIG STATE MACHINE – PROJECT CONFIG SERVICE

## 1. Overview

Project Config lifecycle được quản lý bởi **state machine** với 4 states chính. Mỗi config phải tuân theo các transition rules được định nghĩa rõ ràng.

**Purpose:**
- Đảm bảo consistency của config lifecycle
- Prevent invalid state transitions
- Audit trail cho mọi state changes
- Support rollback và recovery

---

## 2. Configuration States

### 2.1 State Definitions

```java
public enum ProjectConfigState {
    
    /**
     * Config vừa được tạo, chưa verify connection.
     * - Tokens đã được encrypt và lưu DB
        * - Chưa test connection với Jira/GitHub
     * - User có thể update hoặc verify
     */
    DRAFT,
    
    /**
     * Config đã verify successfully.
     * - Connection test passed
     * - Tokens hợp lệ và có đủ permissions
     * - Ready for production use
     */
    VERIFIED,
    
    /**
     * Config verification failed hoặc tokens bị revoked.
     * - Connection test failed
     * - Tokens expired hoặc insufficient permissions
     * - Cần update tokens để recover
     */
    INVALID,
    
    /**
     * Config đã bị soft delete.
     * - deleted_at IS NOT NULL
     * - Không thể update (phải restore trước)
     * - Có thể restore trong 30 ngày
     */
    DELETED
}
```

---

### 2.2 State Properties

| State | Can Update? | Can Verify? | Can Delete? | Can Use for Sync? |
|-------|-------------|-------------|-------------|-------------------|
| `DRAFT` | ✅ YES | ✅ YES | ✅ YES | ⚠️ WARNING (not verified) |
| `VERIFIED` | ✅ YES (triggers re-verify) | ✅ YES | ✅ YES | ✅ YES |
| `INVALID` | ✅ YES (auto re-verify) | ✅ YES | ✅ YES | ❌ NO |
| `DELETED` | ❌ NO | ❌ NO | ❌ NO | ❌ NO |

---

## 3. State Transitions

**CRITICAL: State transitions are SYSTEM-DRIVEN ONLY. No manual state changes allowed.**

### 3.1 Transition Rules

**WHO can trigger state transitions:**
- **Use Cases ONLY:** UC32 (Update), UC33 (Delete), UC34 (Verify), UC35 (Restore)
- **System events:** Token revocation detection, periodic health checks
- **NOT allowed:** LEADER cannot manually set state via API

**Enforcement:**
- No API endpoint to directly set `state` field
- State changes are side-effects of business operations
- All state changes logged in audit trail

---

### 3.2 Transition Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│                    Config Lifecycle                          │
│                                                              │
└──────────────────────────────────────────────────────────────┘

                    ┌─────────────┐
                    │   CREATE    │
                    └──────┬──────┘
                           │
                           ▼
                    ┌─────────────┐
             ┌──────│    DRAFT    │◄──────┐
             │      └──────┬──────┘       │
             │             │              │
             │     ┌───────┴───────┐      │
             │     │               │      │
             │     │ VERIFY        │      │ UPDATE
             │     │ SUCCESS       │      │ (triggers
             │     │               │      │  re-verify)
             │     ▼               ▼      │
             │ ┌─────────────┐  ┌─────────────┐
             │ │  VERIFIED   │  │   INVALID   │
             │ └──────┬──────┘  └──────┬──────┘
             │        │                │
             │        │ TOKEN          │ FIX &
             │        │ REVOKED        │ VERIFY
             │        │                │
             │        ▼                │
             │        └────────────────┘
             │
             │ DELETE
             │
             ▼
      ┌─────────────┐
      │   DELETED   │
      └──────┬──────┘
             │
             │ RESTORE
             │ (within 30 days)
             │
             ▼
      ┌─────────────┐
      │    DRAFT    │
      └─────────────┘
```

---

### 3.2 Valid Transitions

| From State | To State | Trigger | Precondition |
|-----------|----------|---------|--------------|
| `null` | `DRAFT` | **Create Config** | Group exists, no existing config |
| `DRAFT` | `VERIFIED` | **Verify Success** | Connection test passed |
| `DRAFT` | `INVALID` | **Verify Failed** | Connection test failed |
| `DRAFT` | `DELETED` | **Delete** | User/Admin action |
| `VERIFIED` | `INVALID` | **External Event** | Token revoked, API changed |
| `VERIFIED` | `DRAFT` | **Update Config** | User updates tokens/URLs |
| `VERIFIED` | `DELETED` | **Delete** | User/Admin action |
| `INVALID` | `DRAFT` | **Update Config** | User provides new tokens |
| `INVALID` | `VERIFIED` | **Re-verify Success** | Updated tokens are valid |
| `INVALID` | `DELETED` | **Delete** | User/Admin action |
| `DELETED` | `DRAFT` | **Restore** | Within 30 days |

---

### 3.3 Valid Transitions (System-Driven)

| From State | To State | Triggered By | Use Case | Precondition |
|-----------|----------|--------------|----------|--------------|
| `null` | `DRAFT` | **UC30: Create Config** | System creates | Group exists, no existing config |
| `DRAFT` | `VERIFIED` | **UC34: Verify (Success)** | System validates | Connection test passed |
| `DRAFT` | `INVALID` | **UC34: Verify (Failed)** | System validates | Connection test failed |
| `DRAFT` | `DELETED` | **UC33: Delete** | User action | Soft delete |
| `VERIFIED` | `INVALID` | **External Event / Periodic Check** | System detects | Token revoked, API changed |
| `VERIFIED` | `DRAFT` | **UC32: Update (critical fields)** | System auto-resets | User updates tokens/URLs |
| `VERIFIED` | `DELETED` | **UC33: Delete** | User action | Soft delete |
| `INVALID` | `DRAFT` | **UC32: Update** | System auto-resets | User provides new tokens |
| `INVALID` | `VERIFIED` | **UC34: Re-verify (Success)** | System validates | Updated tokens are valid |
| `INVALID` | `DELETED` | **UC33: Delete** | User action | Soft delete |
| `DELETED` | `DRAFT` | **UC35: Restore** | Admin action | Within 90 days |

**Key Points:**
- **UC32 (Update):** When updating critical fields (tokens, URLs), state automatically resets to `DRAFT` (requires re-verification)
- **UC34 (Verify):** System performs connection test and sets state to `VERIFIED` or `INVALID` based on result
- **UC33 (Delete):** Always transitions to `DELETED` regardless of current state
- **UC35 (Restore):** Only restores to `DRAFT` (not `VERIFIED`), requires re-verification

---

### 3.4 Invalid Transitions (FORBIDDEN)

**❌ These transitions are FORBIDDEN:**

| From State | To State | Reason |
|-----------|----------|--------|
| `DELETED` | `VERIFIED` | Must restore to DRAFT first, then verify via UC34 |
| `DELETED` | `INVALID` | Cannot transition to invalid from deleted |
| `INVALID` | `VERIFIED` | Must re-verify explicitly via UC34 (not automatic) |
| `DRAFT` | `DRAFT` | No-op (same state) |
| **ANY** | **ANY** | **Manual state change via API (NO ENDPOINT EXPOSED)** |

**Critical Security Rule:**
- NO API endpoint like `PATCH /api/project-configs/{id}/state` exists
- State is NOT part of UpdateConfigRequest DTO
- Attempting to set state directly → 400 BAD REQUEST

---

## 4. Transition Logic

### 4.1 Create Config → DRAFT

**Trigger:** `POST /api/project-configs`

**Logic:**

```java
@Transactional
public ProjectConfigDTO createConfig(CreateConfigRequest request, UUID userId) {
    // Step 1: Validate & encrypt tokens
    String encryptedJiraToken = encryptionService.encrypt(request.getJiraApiToken());
    String encryptedGithubToken = encryptionService.encrypt(request.getGithubToken());
    
    // Step 2: Create config entity
    ProjectConfig config = ProjectConfig.builder()
        .groupId(request.getGroupId())
        .jiraHostUrl(request.getJiraHostUrl())
        .encryptedJiraToken(encryptedJiraToken)
        .githubRepoUrl(request.getGithubRepoUrl())
        .encryptedGithubToken(encryptedGithubToken)
        .state(ProjectConfigState.DRAFT)  // Initial state
        .createdBy(userId)
        .createdAt(LocalDateTime.now())
        .build();
    
    // Step 3: Save to database
    ProjectConfig savedConfig = projectConfigRepository.save(config);
    
    // Step 4: Audit log
    auditService.log(AuditEvent.builder()
        .action("CONFIG_CREATED")
        .entityType("PROJECT_CONFIG")
        .entityId(savedConfig.getConfigId())
        .oldState(null)
        .newState(ProjectConfigState.DRAFT)
        .actorId(userId)
        .build());
    
    return projectConfigMapper.toDTO(savedConfig);
}
```

**Result:**
- Config state = `DRAFT`
- Config visible to user
- User can verify or use immediately (with warning)

---

### 4.2 DRAFT → VERIFIED (Verify Success)

**Trigger:** `POST /api/project-configs/{id}/verify` returns SUCCESS

**Logic:**

```java
@Transactional
public VerificationResult verifyConfig(UUID configId) {
    ProjectConfig config = projectConfigRepository.findById(configId)
        .orElseThrow(() -> new ConfigNotFoundException(configId));
    
    // Step 1: Decrypt tokens
    String jiraToken = encryptionService.decrypt(config.getEncryptedJiraToken());
    String githubToken = encryptionService.decrypt(config.getEncryptedGithubToken());
    
    // Step 2: Test connections
    ConnectionStatus jiraStatus = jiraClient.testConnection(config.getJiraHostUrl(), jiraToken);
    ConnectionStatus githubStatus = githubClient.testConnection(config.getGithubRepoUrl(), githubToken);
    
    // Step 3: Determine result
    boolean overallSuccess = jiraStatus.isSuccess() && githubStatus.isSuccess();
    
    // Step 4: Update state
    ProjectConfigState oldState = config.getState();
    ProjectConfigState newState = overallSuccess ? ProjectConfigState.VERIFIED : ProjectConfigState.INVALID;
    
    config.setState(newState);
    config.setLastVerifiedAt(LocalDateTime.now());
    config.setVerificationResult(buildVerificationResult(jiraStatus, githubStatus));
    
    projectConfigRepository.save(config);
    
    // Step 5: Audit log
    auditService.log(AuditEvent.builder()
        .action("CONFIG_VERIFIED")
        .entityType("PROJECT_CONFIG")
        .entityId(configId)
        .oldState(oldState)
        .newState(newState)
        .metadata(Map.of(
            "jiraStatus", jiraStatus.getStatus(),
            "githubStatus", githubStatus.getStatus()
        ))
        .build());
    
    return VerificationResult.builder()
        .jiraConnection(jiraStatus)
        .githubConnection(githubStatus)
        .overallStatus(overallSuccess ? "SUCCESS" : "FAILED")
        .build();
}
```

**Result:**
- If success: State = `VERIFIED`
- If failed: State = `INVALID`
- `last_verified_at` updated

---

### 4.3 VERIFIED → DRAFT (Update Config)

**Trigger:** `PUT /api/project-configs/{id}` with token/URL changes

**Logic:**

```java
@Transactional
public ProjectConfigDTO updateConfig(UUID configId, UpdateConfigRequest request) {
    ProjectConfig config = projectConfigRepository.findById(configId)
        .orElseThrow(() -> new ConfigNotFoundException(configId));
    
    ProjectConfigState oldState = config.getState();
    boolean tokensChanged = false;
    
    // Step 1: Update fields
    if (request.getJiraApiToken() != null) {
        config.setEncryptedJiraToken(encryptionService.encrypt(request.getJiraApiToken()));
        tokensChanged = true;
    }
    
    if (request.getGithubToken() != null) {
        config.setEncryptedGithubToken(encryptionService.encrypt(request.getGithubToken()));
        tokensChanged = true;
    }
    
    if (request.getJiraHostUrl() != null) {
        config.setJiraHostUrl(request.getJiraHostUrl());
        tokensChanged = true; // URL change requires re-verify
    }
    
    if (request.getGithubRepoUrl() != null) {
        config.setGithubRepoUrl(request.getGithubRepoUrl());
        tokensChanged = true;
    }
    
    // Step 2: Transition to DRAFT if critical fields changed
    if (tokensChanged && oldState == ProjectConfigState.VERIFIED) {
        config.setState(ProjectConfigState.DRAFT);
        config.setLastVerifiedAt(null); // Clear verification timestamp
    }
    
    config.setUpdatedAt(LocalDateTime.now());
    
    ProjectConfig savedConfig = projectConfigRepository.save(config);
    
    // Step 3: Audit log
    auditService.log(AuditEvent.builder()
        .action("CONFIG_UPDATED")
        .entityType("PROJECT_CONFIG")
        .entityId(configId)
        .oldState(oldState)
        .newState(savedConfig.getState())
        .metadata(Map.of("fieldsChanged", extractChangedFields(request)))
        .build());
    
    return projectConfigMapper.toDTO(savedConfig);
}
```

**Result:**
- State = `DRAFT` (if tokens/URLs changed)
- State remains same (if only metadata changed)
- Verification cleared, user must re-verify

---

### 4.4 VERIFIED → INVALID (External Event)

**Trigger:** Background verification job detects token revocation

**Logic:**

```java
@Scheduled(cron = "0 0 */4 * * *") // Every 4 hours
@Transactional
public void periodicVerification() {
    List<ProjectConfig> verifiedConfigs = projectConfigRepository.findByState(ProjectConfigState.VERIFIED);
    
    for (ProjectConfig config : verifiedConfigs) {
        try {
            // Test connection
            String jiraToken = encryptionService.decrypt(config.getEncryptedJiraToken());
            ConnectionStatus jiraStatus = jiraClient.testConnection(config.getJiraHostUrl(), jiraToken);
            
            String githubToken = encryptionService.decrypt(config.getEncryptedGithubToken());
            ConnectionStatus githubStatus = githubClient.testConnection(config.getGithubRepoUrl(), githubToken);
            
            // If either fails, mark as INVALID
            if (!jiraStatus.isSuccess() || !githubStatus.isSuccess()) {
                config.setState(ProjectConfigState.INVALID);
                config.setInvalidReason(buildInvalidReason(jiraStatus, githubStatus));
                projectConfigRepository.save(config);
                
                // Notify team leader
                notificationService.sendConfigInvalidNotification(config);
                
                // Audit log
                auditService.log(AuditEvent.builder()
                    .action("CONFIG_INVALIDATED")
                    .entityType("PROJECT_CONFIG")
                    .entityId(config.getConfigId())
                    .oldState(ProjectConfigState.VERIFIED)
                    .newState(ProjectConfigState.INVALID)
                    .actorId(null) // System action
                    .metadata(Map.of(
                        "jiraStatus", jiraStatus.getStatus(),
                        "githubStatus", githubStatus.getStatus()
                    ))
                    .build());
            }
            
        } catch (Exception e) {
            logger.error("Failed to verify config {}", config.getConfigId(), e);
        }
    }
}
```

**Result:**
- State = `INVALID`
- Notification sent to team leader
- Sync service will reject config for syncing

---

### 4.5 ANY → DELETED (Soft Delete)

**Trigger:** `DELETE /api/project-configs/{id}`

**Logic:**

```java
@Transactional
public void deleteConfig(UUID configId, UUID deletedBy) {
    ProjectConfig config = projectConfigRepository.findById(configId)
        .orElseThrow(() -> new ConfigNotFoundException(configId));
    
    ProjectConfigState oldState = config.getState();
    
    // Step 1: Set soft delete fields
    config.setDeletedAt(LocalDateTime.now());
    config.setDeletedBy(deletedBy);
    config.setState(ProjectConfigState.DELETED);
    
    // Step 2: Revoke all access (optional: clear sensitive data)
    // config.setEncryptedJiraToken(null);
    // config.setEncryptedGithubToken(null);
    
    projectConfigRepository.save(config);
    
    // Step 3: Audit log
    auditService.log(AuditEvent.builder()
        .action("CONFIG_DELETED")
        .entityType("PROJECT_CONFIG")
        .entityId(configId)
        .oldState(oldState)
        .newState(ProjectConfigState.DELETED)
        .actorId(deletedBy)
        .build());
}
```

**Result:**
- State = `DELETED`
- `deleted_at` and `deleted_by` set
- Config excluded from queries (via `@SQLRestriction`)

---

### 4.6 DELETED → DRAFT (Restore)

**Trigger:** `POST /api/project-configs/{id}/restore`

**Logic:**

```java
@Transactional
public ProjectConfigDTO restoreConfig(UUID configId, UUID restoredBy) {
    // Step 1: Query including deleted records
    ProjectConfig config = projectConfigRepository.findByIdIncludingDeleted(configId)
        .orElseThrow(() -> new ConfigNotFoundException(configId));
    
    // Step 2: Validate restore eligibility
    if (config.getDeletedAt() == null) {
        throw new ConfigNotDeletedException("Config is not deleted");
    }
    
    if (config.getDeletedAt().isBefore(LocalDateTime.now().minusDays(30))) {
        throw new ConfigExpiredException("Config deleted more than 30 days ago");
    }
    
    // Step 3: Restore config
    ProjectConfigState oldState = config.getState();
    
    config.setDeletedAt(null);
    config.setDeletedBy(null);
    config.setState(ProjectConfigState.DRAFT); // Reset to DRAFT, user must re-verify
    config.setLastVerifiedAt(null);
    
    ProjectConfig restoredConfig = projectConfigRepository.save(config);
    
    // Step 4: Audit log
    auditService.log(AuditEvent.builder()
        .action("CONFIG_RESTORED")
        .entityType("PROJECT_CONFIG")
        .entityId(configId)
        .oldState(oldState)
        .newState(ProjectConfigState.DRAFT)
        .actorId(restoredBy)
        .build());
    
    return projectConfigMapper.toDTO(restoredConfig);
}
```

**Result:**
- State = `DRAFT`
- `deleted_at` and `deleted_by` cleared
- Config visible again, requires re-verification

---

## 5. State-Based Business Rules

### 5.1 Sync Service Usage Rules

```java
@Service
public class ProjectConfigValidationService {
    
    /**
     * Determine if config can be used for syncing
     */
    public ConfigUsageValidation validateForSync(ProjectConfig config) {
        return switch (config.getState()) {
            case VERIFIED -> ConfigUsageValidation.ALLOWED;
            
            case DRAFT -> ConfigUsageValidation.builder()
                .allowed(true)
                .warning("Config not verified - sync may fail")
                .build();
            
            case INVALID -> ConfigUsageValidation.builder()
                .allowed(false)
                .error("Config is invalid - cannot use for sync")
                .build();
            
            case DELETED -> ConfigUsageValidation.builder()
                .allowed(false)
                .error("Config is deleted")
                .build();
        };
    }
}
```

---

### 5.2 Update Permission Rules

```java
public boolean canUpdate(ProjectConfig config) {
    return config.getState() != ProjectConfigState.DELETED;
}

public boolean canDelete(ProjectConfig config) {
    return config.getState() != ProjectConfigState.DELETED;
}

public boolean canVerify(ProjectConfig config) {
    return config.getState() != ProjectConfigState.DELETED;
}
```

---

## 6. Database Schema

### 6.1 State Column

```sql
ALTER TABLE project_configs ADD COLUMN state VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE project_configs ADD COLUMN last_verified_at TIMESTAMP NULL;
ALTER TABLE project_configs ADD COLUMN verification_result JSONB NULL;
ALTER TABLE project_configs ADD COLUMN invalid_reason TEXT NULL;

-- Index for state queries
CREATE INDEX idx_project_configs_state ON project_configs(state);

-- Check constraint
ALTER TABLE project_configs ADD CONSTRAINT chk_state 
    CHECK (state IN ('DRAFT', 'VERIFIED', 'INVALID', 'DELETED'));
```

---

### 6.2 State Transition Audit

```sql
CREATE TABLE config_state_transitions (
    transition_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id UUID NOT NULL REFERENCES project_configs(config_id),
    from_state VARCHAR(20),
    to_state VARCHAR(20) NOT NULL,
    trigger_action VARCHAR(50) NOT NULL,
    actor_id UUID REFERENCES users(user_id),
    transitioned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    metadata JSONB,
    
    CONSTRAINT chk_from_state CHECK (from_state IN ('DRAFT', 'VERIFIED', 'INVALID', 'DELETED')),
    CONSTRAINT chk_to_state CHECK (to_state IN ('DRAFT', 'VERIFIED', 'INVALID', 'DELETED'))
);

CREATE INDEX idx_state_transitions_config ON config_state_transitions(config_id);
CREATE INDEX idx_state_transitions_date ON config_state_transitions(transitioned_at);
```

---

## 7. Monitoring & Metrics

### 7.1 State Distribution Metrics

```java
@Component
public class ConfigStateMetrics {
    
    private final MeterRegistry meterRegistry;
    private final ProjectConfigRepository repository;
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void recordStateDistribution() {
        Map<ProjectConfigState, Long> distribution = repository.countByState();
        
        distribution.forEach((state, count) -> {
            meterRegistry.gauge(
                "project_config.state.count",
                Tags.of("state", state.name()),
                count
            );
        });
    }
}
```

---

### 7.2 Transition Alerts

**Alert on suspicious patterns:**

```yaml
alerts:
  - name: HighInvalidRate
    condition: (rate(config_state_transitions{to_state="INVALID"}[5m]) > 10)
    severity: warning
    message: "High rate of configs becoming INVALID"
    
  - name: VerifiedToInvalid
    condition: (config_state_transitions{from_state="VERIFIED", to_state="INVALID"})
    severity: critical
    message: "Verified config became invalid - possible token revocation"
```

---

## 8. State Machine Testing

### 8.1 Transition Tests

```java
@SpringBootTest
public class ConfigStateMachineTest {
    
    @Test
    void testCreateConfig_InitialStateIsDraft() {
        // Create config
        ProjectConfig config = createConfig(validRequest);
        
        // Verify
        assertThat(config.getState()).isEqualTo(ProjectConfigState.DRAFT);
    }
    
    @Test
    void testVerifySuccess_TransitionToDraftToVerified() {
        // Given: Config in DRAFT state
        ProjectConfig config = createConfig(validRequest);
        assertThat(config.getState()).isEqualTo(ProjectConfigState.DRAFT);
        
        // When: Verify success
        mockJiraClient.willReturnSuccess();
        mockGithubClient.willReturnSuccess();
        verificationService.verify(config.getConfigId());
        
        // Then: State = VERIFIED
        ProjectConfig updated = repository.findById(config.getConfigId()).get();
        assertThat(updated.getState()).isEqualTo(ProjectConfigState.VERIFIED);
    }
    
    @Test
    void testVerifyFailed_TransitionToDraftToInvalid() {
        // Given: Config in DRAFT state
        ProjectConfig config = createConfig(validRequest);
        
        // When: Verify failed
        mockJiraClient.willReturnError();
        verificationService.verify(config.getConfigId());
        
        // Then: State = INVALID
        ProjectConfig updated = repository.findById(config.getConfigId()).get();
        assertThat(updated.getState()).isEqualTo(ProjectConfigState.INVALID);
    }
    
    @Test
    void testUpdateVerifiedConfig_TransitionToDraft() {
        // Given: Config in VERIFIED state
        ProjectConfig config = createVerifiedConfig();
        assertThat(config.getState()).isEqualTo(ProjectConfigState.VERIFIED);
        
        // When: Update token
        UpdateConfigRequest updateRequest = new UpdateConfigRequest();
        updateRequest.setJiraApiToken("NEW_TOKEN");
        configService.updateConfig(config.getConfigId(), updateRequest);
        
        // Then: State = DRAFT
        ProjectConfig updated = repository.findById(config.getConfigId()).get();
        assertThat(updated.getState()).isEqualTo(ProjectConfigState.DRAFT);
        assertThat(updated.getLastVerifiedAt()).isNull();
    }
    
    @Test
    void testDeleteConfig_TransitionToDeleted() {
        // Given: Config in any state
        ProjectConfig config = createConfig(validRequest);
        
        // When: Delete
        configService.deleteConfig(config.getConfigId(), adminUserId);
        
        // Then: State = DELETED
        ProjectConfig updated = repository.findByIdIncludingDeleted(config.getConfigId()).get();
        assertThat(updated.getState()).isEqualTo(ProjectConfigState.DELETED);
        assertThat(updated.getDeletedAt()).isNotNull();
    }
    
    @Test
    void testRestoreDeletedConfig_TransitionToDraft() {
        // Given: Config in DELETED state
        ProjectConfig config = createDeletedConfig();
        assertThat(config.getState()).isEqualTo(ProjectConfigState.DELETED);
        
        // When: Restore
        configService.restoreConfig(config.getConfigId(), adminUserId);
        
        // Then: State = DRAFT
        ProjectConfig restored = repository.findById(config.getConfigId()).get();
        assertThat(restored.getState()).isEqualTo(ProjectConfigState.DRAFT);
        assertThat(restored.getDeletedAt()).isNull();
    }
    
    @Test
    void testInvalidTransition_DeletedToVerified_ThrowsException() {
        // Given: Config in DELETED state
        ProjectConfig config = createDeletedConfig();
        
        // When/Then: Direct transition to VERIFIED throws exception
        assertThatThrownBy(() -> {
            config.setState(ProjectConfigState.VERIFIED); // Invalid!
            repository.save(config);
        }).isInstanceOf(IllegalStateTransitionException.class);
    }
}
```

---

## 9. UI Indicators

### 9.1 State Badge Display

**Frontend component:**

```typescript
interface ConfigStateBadge {
  state: 'DRAFT' | 'VERIFIED' | 'INVALID' | 'DELETED';
}

function getStateBadge(state: string) {
  switch(state) {
    case 'DRAFT':
      return { color: 'blue', icon: 'draft', text: 'Draft - Not Verified' };
    case 'VERIFIED':
      return { color: 'green', icon: 'check-circle', text: 'Verified' };
    case 'INVALID':
      return { color: 'red', icon: 'error', text: 'Invalid - Needs Update' };
    case 'DELETED':
      return { color: 'gray', icon: 'trash', text: 'Deleted' };
  }
}
```

---

### 9.2 Actionable UI Based on State

| State | Available Actions |
|-------|------------------|
| `DRAFT` | ✅ Edit, ✅ Verify, ✅ Delete |
| `VERIFIED` | ✅ Edit (→ DRAFT), ✅ Re-verify, ✅ Delete |
| `INVALID` | ✅ Edit (→ DRAFT), ✅ Re-verify, ✅ Delete, ⚠️ Warning: Cannot sync |
| `DELETED` | ✅ Restore (within 30 days), ❌ All other actions disabled |

---

## 10. Best Practices

### 10.1 State Transition Guidelines

1. **Always use service methods** for state transitions (not direct setter)
2. **Log every transition** in audit trail
3. **Validate transition** before applying
4. **Notify stakeholders** on critical transitions (VERIFIED → INVALID)
5. **Clear derived fields** when transitioning (e.g., clear `last_verified_at` on VERIFIED → DRAFT)

---

### 10.2 Error Handling

```java
public class IllegalStateTransitionException extends RuntimeException {
    private final ProjectConfigState fromState;
    private final ProjectConfigState toState;
    
    public IllegalStateTransitionException(ProjectConfigState from, ProjectConfigState to) {
        super(String.format("Invalid state transition: %s → %s", from, to));
        this.fromState = from;
        this.toState = to;
    }
}
```

---

**Document Version:** 1.0  
**Last Updated:** 2026-01-29  
**Owner:** Backend Architecture Team  
**Review Schedule:** Quarterly
