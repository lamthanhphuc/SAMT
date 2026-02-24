# ADR-003: Package Naming Standardization - Project Config Service

**Date:** February 9, 2026  
**Status:** ✅ Accepted  
**Deciders:** Tech Lead, Backend Team  

---

## Context

### Current State

Package naming across SAMT services shows inconsistency:

| Service | Base Package | Status |
|---------|--------------|--------|
| API Gateway | `com.example.gateway` | ✅ Standard |
| Identity Service | `com.example.identityservice` | ✅ Standard |
| User-Group Service | `com.example.user_groupservice` | ✅ Standard (underscore acceptable) |
| **Project Config Service** | **`com.fpt.projectconfig`** | ❌ **DEVIATION** |
| Analysis Service | `com.example.analysisservice` | ✅ Standard |
| Sync Service | `com.example.syncservice` | ✅ Standard |
| Report Service | `com.example.reportservice` | ✅ Standard |
| Notification Service | `com.example.notificationservice` | ✅ Standard |

### Problem Statement

**Issue:** Project Config Service uses `com.fpt.projectconfig` while all other services use `com.example.*`

**Impact:**
1. **Consistency:** Breaks naming pattern across the system
2. **Developer Experience:** 
   - IDE autocomplete shows mixed package prefixes (`com.example.*` and `com.fpt.*`)
   - Confusing for new developers
   - Suggests different ownership/vendor
3. **Build Scripts:** Some automated tools may assume uniform package structure
4. **Code Reviews:** Reviewers must remember different package convention

**Root Cause Analysis:**
- Likely: Project Config was developed by different team or external vendor (FPT)
- Or: Started as separate project then integrated into SAMT

---

## Decision

**We MANDATE package naming standardization to `com.example.projectconfigservice` for all services in the SAMT system.**

### Rationale

**Option A (Selected): Refactor to `com.example.projectconfigservice`**

✅ **Pros:**
- Consistency with 7 other services
- Single namespace for entire SAMT system
- Better IDE autocomplete experience
- Easier code search across repositories
- No special case to remember
- Aligns with company domain (example.com)

❌ **Cons:**
- Requires refactoring (2-4 hours estimated)
- Need to update imports in 50+ files
- Must update protobuf package declarations
- Temporary risk if refactoring introduces bugs

**Mitigation:**
- Use IDE automated refactoring (IntelliJ/VSCode)
- Low risk - package rename is well-supported operation
- Full test suite will catch any issues

---

**Option B (Rejected): Keep `com.fpt.projectconfig`**

✅ **Pros:**
- No code changes required
- No risk of refactoring bugs
- Preserves vendor branding if required

❌ **Cons:**
- Permanent inconsistency in codebase
- Must document exception (more cognitive load)
- Confusing for new developers
- May indicate lack of ownership clarity

**Decision:** Option A provides long-term value despite short-term effort.

---

## Implementation Plan (PHASE 2)

### Preparation

1. **Branch Creation:**
   ```bash
   git checkout -b refactor/standardize-project-config-package
   ```

2. **Backup:**
   ```bash
   git tag before-package-refactor
   ```

### Step-by-Step Refactoring

#### 1. Rename Package in IDE

**IntelliJ IDEA:**
1. Right-click `com.fpt.projectconfig` package
2. Refactor → Rename
3. Enter new name: `com.example.projectconfigservice`
4. Check "Search in comments and strings"
5. Check "Search for text occurrences"
6. Preview changes → Refactor

**VS Code:**
1. Use Find & Replace (Ctrl+Shift+H)
2. Find: `com.fpt.projectconfig`
3. Replace: `com.example.projectconfigservice`
4. Files to include: `*.java`, `*.proto`, `*.xml`

#### 2. Update Protobuf Files

**File:** `common-events/src/main/proto/projectconfig_service.proto`

```protobuf
// Before
package com.fpt.projectconfig;
option java_package = "com.fpt.projectconfig.grpc";

// After
package com.example.projectconfigservice;
option java_package = "com.example.projectconfigservice.grpc";
```

**Regenerate:**
```bash
cd project-config-service
mvn clean compile  # Regenerates proto stubs
```

#### 3. Update Maven Configuration (if needed)

**File:** `project-config-service/pom.xml`

Check if any plugins reference package name:
```xml
<!-- Unlikely, but verify -->
<plugin>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-maven-plugin</artifactId>
  <!-- Usually auto-detects main class -->
</plugin>
```

#### 4. Update Application Properties

**File:** `project-config-service/src/main/resources/application.yml`

Check if any configurations reference package:
```yaml
# Usually none, but verify
logging:
  level:
    com.example.projectconfigservice: DEBUG  # Update if exists
```

#### 5. Update Database Migration Comments (Optional)

**File:** `project-config-service/src/main/resources/db/migration/*.sql`

```sql
-- Before
COMMENT ON TABLE project_configs IS 'Managed by com.fpt.projectconfig.entity.ProjectConfig';

-- After
COMMENT ON TABLE project_configs IS 'Managed by com.example.projectconfigservice.entity.ProjectConfig';
```

#### 6. Update Documentation

**Files to update:**
- `docs/ProjectConfig/*.md` (if any package references)
- `project-config-service/README.md`

---

### Files Affected (Estimated)

| File Type | Count | Update Method |
|-----------|-------|---------------|
| `*.java` (source) | ~40 | IDE refactor |
| `*.java` (test) | ~15 | IDE refactor |
| `*.proto` | 2 | Manual |
| `application.yml` | 1 | Manual |
| `pom.xml` | 1 | Verify |
| Documentation | 3 | Manual |

**Total Effort:** 2-4 hours

---

## Testing Strategy

### 1. Compilation Test
```bash
cd project-config-service
mvn clean compile
# Must succeed with no errors
```

### 2. Unit Tests
```bash
mvn test
# All tests must pass
```

### 3. Integration Tests
```bash
mvn verify -P integration-tests
# gRPC client calls must work (package name in stubs)
```

### 4. gRPC Communication Test

**From User-Group Service:**
```java
// UserGroupServiceGrpcClient должен work with new package
boolean exists = userGroupClient.verifyGroupExists(groupId);
// Should return correct result (package change is transparent)
```

**From Sync Service (Planned):**
```java
// Will use new package when implemented
projectConfigClient.getDecryptedTokens(configId);
```

### 5. Docker Build Test
```bash
docker-compose -f docker-compose-implementation.yml build project-config-service
docker-compose up project-config-service
# Check logs for startup errors
```

### 6. Health Check
```bash
curl http://localhost:8083/actuator/health
# Should return {"status":"UP"}
```

---

## Rollback Plan

If refactoring causes issues:

1. **Quick Rollback:**
   ```bash
   git reset --hard before-package-refactor
   git clean -fd
   ```

2. **Selective Rollback:** Use git to revert specific commits

3. **Partial Fix:** If only one file is problematic, manually revert that file

**Risk Level:** 🟢 LOW (IDE refactoring is reliable, full test coverage exists)

---

## Consequences

### ✅ Positive

1. **Consistency:** All 8 services now use `com.example.*` namespace
2. **Developer Experience:** 
   - Autocomplete shows unified package structure
   - Easier to navigate codebase
   - No exceptions to remember
3. **Code Quality:**
   - Easier to write cross-service code generators
   - Uniform code review standards
4. **Onboarding:** New developers see consistent architecture

### ⚠️ Neutral

1. **Proto Package Change:** Client stubs must be regenerated
   - User-Group Service: Uses Project Config as gRPC client
   - Sync Service: Will use Project Config when implemented
   - **Action:** Rebuild both after refactoring

2. **Import Statements:** All imports automatically updated by IDE

### ❌ Negative (Minimal)

1. **Git History:** Blame tracking may be disrupted for renamed files
   - **Mitigation:** Use `git blame -C` to follow moved code
   - One-time issue, acceptable for long-term benefit

2. **Active Branches:** Merge conflicts if other branches modify affected files
   - **Mitigation:** Coordinate refactoring with team, merge timing

---

## Standards & Conventions

### Package Naming Rules (Formalized)

For all SAMT services:

```
<domain>.<company>.<service_name>
    ↓         ↓           ↓
com.example.identityservice      ✅
com.example.user_groupservice    ✅ (underscore OK for readability)
com.example.projectconfigservice ✅
com.fpt.projectconfig            ❌ (violates domain consistency)
```

**Exception:** `com.example.common.*` for shared libraries

### Sub-package Structure (Standard)

```
com.example.projectconfigservice/
├── config/              # Spring configuration
├── controller/          # REST controllers
├── dto/                 # Data transfer objects
├── entity/              # JPA entities
├── repository/          # Database repositories
├── service/             # Business logic
├── security/            # Security filters, utilities
├── grpc/                # gRPC server implementations
├── client/              # gRPC clients to other services
├── exception/           # Custom exceptions
├── scheduler/           # Scheduled tasks
└── util/                # Utilities
```

---

## Communication Plan

### Team Notification

**Before Refactoring:**
> "🔔 **Package Refactoring Notice**  
> Project Config Service package will be renamed:  
> `com.fpt.projectconfig` → `com.example.projectconfigservice`  
> 
> **When:** [Date]  
> **Duration:** ~4 hours  
> **Impact:** Must rebuild services that depend on Project Config gRPC stubs  
> **Affected Services:** User-Group Service, Sync Service  
> 
> **Action Required:**  
> - Pull latest changes after refactoring complete  
> - Run `mvn clean install` to regenerate proto stubs  
> - Run tests to verify gRPC calls still work"

### Documentation Updates

- [x] ADR-003 (this document)
- [ ] Update README.md (after refactoring)
- [ ] Update IMPLEMENTATION_GUIDE.md package references
- [ ] Update ProjectConfig/10_CODE_WALKTHROUGH.md

---

## References

- [Java Package Naming Conventions](https://docs.oracle.com/javase/tutorial/java/package/namingpkgs.html)
- [Architecture Compliance Report](../ARCHITECTURE_COMPLIANCE_REPORT.md#package-naming-convention)
- [IntelliJ Refactoring Guide](https://www.jetbrains.com/help/idea/rename-refactorings.html)

---

## Alternatives Considered

### Alternative 1: Use `com.fpt.*` for ALL Services
**Status:** Rejected

**Reasoning:**
- Would require refactoring 7 services instead of 1
- `example.com` is the documented system domain
- Changing all services is higher risk

### Alternative 2: Use `com.samt.*` (New Domain)
**Status:** Rejected

**Reasoning:**
- Would require refactoring ALL 8 services
- No clear benefit over `com.example.*`
- SAMT is a product name, not a company domain

### Alternative 3: Keep `com.fpt.*` and Document as Exception
**Status:** Rejected

See "Decision" section for rationale.

---

## Sign-off

- [x] Tech Lead: Approved - Feb 9, 2026
- [x] Backend Team: Approved - Feb 9, 2026
- [ ] Team Members: Notified (after PHASE 2 implementation)

**Implementation Target:** Sprint 2, Week 2  
**Estimated Effort:** 2-4 hours  
**Risk Level:** 🟢 LOW  

**Next Review:** After refactoring complete and tests pass  
**Final Sign-off:** After production deployment
