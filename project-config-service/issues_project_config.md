# Issues & Clarifications - Project Config Service (gRPC)

**Ngày tạo:** 2026-02-04  
**Cập nhật:** 2026-02-04 (Chuyển sang gRPC)  
**Mục đích:** Ghi nhận các điểm mâu thuẫn, thiếu thông tin hoặc cần làm rõ trong quá trình triển khai

---

## IMPORTANT: gRPC Implementation

**Architecture Change:** Service đã được chuyển đổi từ REST API sang **gRPC communication**.

**User Responsibilities:**
1. Tạo file `.proto` từ template: `src/main/proto/project_config.proto.template`
2. Configure protobuf-maven-plugin trong pom.xml
3. Run `mvn clean compile` để generate Java Protobuf classes
4. Uncomment implementation code trong:
   - `ProjectConfigGrpcService.java`
   - `ProjectConfigMapper.java`
5. Setup gRPC server (recommend: net.devh:grpc-server-spring-boot-starter)

**Authentication Model:**
- API Gateway validates JWT và forwards userId/roles qua gRPC metadata
- Service chỉ trust metadata, không tự validate JWT
- Internal methods yêu cầu `x-service-name` và `x-service-key` metadata

---

## 1. Mâu thuẫn về kiểu dữ liệu deleted_by

**Vấn đề:**
- **Yêu cầu user:** deleted_by có kiểu UUID
- **Tài liệu thiết kế (03_Database_Design.md):** deleted_by có kiểu BIGINT (Long)

**Quyết định:** 
- Tuân theo tài liệu thiết kế chính thức → Sử dụng `BIGINT` (Long)
- Lý do: Phù hợp với Identity Service (users.id là BIGINT)

---

## 2. Mâu thuẫn về các trường Jira

**Vấn đề:**
- **Yêu cầu user:** jira_url, jira_username, jira_api_token
- **Tài liệu thiết kế:** jira_host_url, jira_api_token_encrypted (KHÔNG có jira_username)

**Quyết định:**
- Tuân theo tài liệu thiết kế → Sử dụng `jira_host_url` và `jira_api_token_encrypted`
- Jira Cloud API sử dụng email trong Basic Auth, không cần trường riêng jira_username
- API token format: `ATATT...` (Jira Cloud API Token)

---

## 3. Token Encryption Implementation

**Vấn đề:**
- Cần triển khai mã hóa AES-256-GCM cho tokens
- Tài liệu có spec rõ ràng nhưng cần thư viện javax.crypto

**Quyết định:**
- Sử dụng Java Cryptography Architecture (JCA) built-in
- Algorithm: AES/GCM/NoPadding
- Key: 256-bit (từ environment variable)
- IV: 96-bit random per encryption
- Format: `{iv_base64}:{ciphertext_base64}`

---

## 4. Token Masking Rules

**Vấn đề:**
- Cần mask tokens cho STUDENT nhưng show full cho ADMIN/LECTURER

**Quyết định:**
- Jira token: `***` + last 4 chars (ví dụ: `***ab12`)
- GitHub token: `ghp_***` + last 4 chars (ví dụ: `ghp_***xyz9`)
- Role check: ADMIN/LECTURER xem full, STUDENT xem masked

---

## 5. Communication với User-Group Service

**Vấn đề:**
- Không dùng gRPC theo yêu cầu
- Cần validate group exists và check group leader

**Quyết định:**
- Sử dụng REST client (RestTemplate)
- Mock endpoints (TODO: Cập nhật khi User-Group Service ready):
  - `GET /api/groups/{groupId}` → Validate group exists
  - Check leader từ response (giả định có trường leaderId)

---

## 6. Verification Logic

**Vấn đề:**
- UC34 yêu cầu verify connection tới Jira/GitHub API
- Chưa rõ logic chi tiết

**Quyết định:**
- Jira: Call `GET {jira_host_url}/rest/api/3/myself` với Bearer token
- GitHub: Call `GET https://api.github.com/user` với token header
- Timeout: 10 seconds
- Success (200 OK) → VERIFIED state
- Fail → INVALID state với error message

---

## 7. State Machine

**Vấn đề:**
- Entity cần support state machine: DRAFT → VERIFIED → INVALID → DELETED

**Quyết định:**
- Enum: ConfigState {DRAFT, VERIFIED, INVALID, DELETED}
- Transitions:
  - CREATE → DRAFT
  - VERIFY success → VERIFIED
  - VERIFY fail → INVALID
  - UPDATE critical fields → DRAFT (require re-verification)
  - DELETE → DELETED (soft delete)

---

## 8. Authorization Logic

**Vấn đề:**
- Chưa rõ logic check "chỉ Group Leader hoặc Admin mới có quyền"

**Quyết định:**
- Extract userId và roles từ JWT claims
- ADMIN: Has "ADMIN" role → Full access
- LECTURER: Has "LECTURER" role → Full access  
- Group Leader: Call User-Group Service để verify user là leader của group
- STUDENT (member): Read-only với masked tokens

---

## 9. Internal API Authentication

**Vấn đề:**
- Sync Service cần lấy decrypted tokens
- Cần cơ chế xác thực service-to-service

**Quyết định:**
- Headers: `X-Service-Name` và `X-Service-Key`
- Validate trong ServiceToServiceAuthFilter
- Chỉ áp dụng cho endpoints `/internal/**`
- Không check JWT cho internal endpoints

---

## 10. Soft Delete Cleanup Job

**Vấn đề:**
- Retention 90 days, cần scheduled job để hard delete

**Quyết định:**
- Spring @Scheduled job chạy daily lúc 2:00 AM
- Query: `DELETE FROM project_configs WHERE deleted_at < NOW() - INTERVAL '90 days'`
- Log số lượng records đã xóa

---

## Technical Debt & Future Enhancements

1. **Rate Limiting:** Chưa implement rate limiting cho verification endpoint
2. **Audit Logging:** Chưa implement audit trail cho các thao tác CRUD
3. **Key Rotation:** Chưa có mechanism để rotate encryption key
4. **Circuit Breaker:** Chưa có circuit breaker cho external API calls (Jira/GitHub)
5. **Caching:** Chưa cache group validation results

---

## Assumptions Made

1. JWT secret key được share giữa các services qua environment variable
2. User-Group Service expose REST endpoints (không phải gRPC)
3. Jira Cloud API tokens có format `ATATT...`
4. GitHub Personal Access Tokens có format `ghp_...`
5. Database migration sử dụng Flyway (hoặc manual SQL scripts)

---

**Note:** File này sẽ được cập nhật khi có thêm clarifications hoặc phát hiện issues mới.
