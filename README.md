# ===================================================
# SAMT MICROSERVICES PROJECT
# ===================================================

## 📖 Giới thiệu

Dự án **SAMT** (Spring Boot Microservices Architecture) bao gồm:
- **API Gateway**: Cổng vào duy nhất cho tất cả requests
- **Identity Service**: Xác thực, phân quyền, quản lý user (DB riêng)
- **Sync Service**: Đồng bộ dữ liệu giữa các hệ thống
- **Analysis Service**: Phân tích dữ liệu
- **Report Service**: Tạo báo cáo
- **Notification Service**: Gửi thông báo

**Tech Stack:**
- Java 21 + Spring Boot 3.2.2
- PostgreSQL 15 (2 databases: identity, core)
- Redis 7
- Docker + Docker Compose

---

## 🚀 Hướng dẫn Deploy

### 1️⃣ Prerequisites
```bash
# Cài đặt
- Java 21 (Eclipse Temurin)
- Maven 3.8+
- Docker & Docker Compose
- PostgreSQL Client (optional)
```

### 2️⃣ Cấu hình môi trường

```bash
# Copy file .env.example thành .env
cp .env.example .env

# Sửa credentials trong .env (QUAN TRỌNG!)
POSTGRES_PASSWORD=your_secure_password_here
```

### 3️⃣ Build & Run

**Option A: Docker (Recommended for Production)**
```bash
# Build tất cả services
./mvnw clean package -DskipTests

# Start toàn bộ hệ thống
docker-compose up -d

# Kiểm tra logs
docker-compose logs -f

# Kiểm tra health
curl http://localhost:9080/actuator/health
```

**Option B: IDE Development (IntelliJ/Eclipse)**
```bash
# Chỉ chạy databases & redis
docker-compose up -d postgres-identity postgres-core redis

# Run từng service từ IDE với profile "default"
# identity-service: port 8081
# sync-service: port 8082
# analysis-service: port 8083
# report-service: port 8084
# notification-service: port 8085
# api-gateway: port 9080 (profile "local")
```

---

## 📡 Service Endpoints

| Service              | Port (Local) | Port (Docker) | Health Check                           |
|----------------------|--------------|---------------|----------------------------------------|
| API Gateway          | 9080         | 9080          | http://localhost:9080/actuator/health  |
| Identity Service     | 8081         | 8081          | http://localhost:8081/actuator/health  |
| Sync Service         | 8082         | 8082          | http://localhost:8082/actuator/health  |
| Analysis Service     | 8083         | 8083          | http://localhost:8083/actuator/health  |
| Report Service       | 8084         | 8084          | http://localhost:8084/actuator/health  |
| Notification Service | 8085         | 8085          | http://localhost:8085/actuator/health  |

**API Routes (via Gateway):**
- `/identity/**` → Identity Service
- `/sync/**` → Sync Service
- `/analysis/**` → Analysis Service
- `/report/**` → Report Service
- `/notification/**` → Notification Service

---

## 🗄️ Database Schema

**postgres-identity (port 5432)**
- Database: `samt_identity`
- Tables: users, roles, permissions, sessions

**postgres-core (port 5433)**
- Database: `samt_core`
- Tables: sync_logs, analysis_data, reports, notifications

**Redis (port 6379)**
- Session storage
- Cache layer

---

## 🔧 Configuration Management

### Profiles
- **default**: Local development (localhost databases)
- **docker**: Container deployment (service names)
- **local** (api-gateway only): IDE development with localhost service URLs

### Environment Variables
```bash
# Database
POSTGRES_USER=postgres
POSTGRES_PASSWORD=12345
DB_IDENTITY_NAME=samt_identity
DB_CORE_NAME=samt_core

# Ports
API_GATEWAY_PORT=9080
IDENTITY_SERVICE_PORT=8081

# Spring
SPRING_PROFILES_ACTIVE=docker

# Java
JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC
```

---

## 🛠️ Troubleshooting

### Database Connection Issues
```bash
# Kiểm tra databases đã chạy chưa
docker ps | grep postgres

# Xem logs database
docker logs postgres-identity
docker logs postgres-core

# Connect để test
psql -h localhost -p 5432 -U postgres -d samt_identity
```

### Build Errors
```bash
# Clean và build lại
./mvnw clean package -DskipTests

# Xoá cache Docker
docker system prune -a
```

### Port Conflicts
```bash
# Kiểm tra port đang bị chiếm
netstat -ano | findstr :5432
netstat -ano | findstr :5433
netstat -ano | findstr :6379
```

---

## 📊 Monitoring & Logs

```bash
# View logs tất cả services
docker-compose logs -f

# View logs 1 service cụ thể
docker-compose logs -f identity-service

# View metrics
curl http://localhost:8081/actuator/metrics
```

---

## 🔐 Security Checklist

- [ ] Đổi `POSTGRES_PASSWORD` trong `.env`
- [ ] Không commit file `.env` vào Git
- [ ] Bật SSL/TLS cho PostgreSQL trong production
- [ ] Cấu hình Redis password
- [ ] Sử dụng Docker secrets cho credentials
- [ ] Giới hạn resource limits cho containers

---

## 📝 Best Practices

### Docker
- ✅ Multi-stage builds (giảm image size)
- ✅ Non-root user (security)
- ✅ Health checks (reliability)
- ✅ Resource limits (stability)
- ✅ Alpine images (smaller footprint)

### Spring Boot
- ✅ Graceful shutdown
- ✅ Connection pooling (HikariCP)
- ✅ Actuator endpoints
- ✅ Profile-based configuration
- ✅ Environment variable injection

### Database
- ✅ 2 databases riêng biệt (identity vs core)
- ✅ Connection pool tuning
- ✅ Timezone configuration
- ✅ Persistent volumes

---

## 📦 Deployment Strategies

### Development
```bash
docker-compose up -d postgres-identity postgres-core redis
# Run services from IDE
```

### Staging/Production
```bash
# Sử dụng docker-compose với .env production
SPRING_PROFILES_ACTIVE=docker docker-compose up -d

# Hoặc Kubernetes (tạo deployment yamls)
kubectl apply -f k8s/
```

---

## 🤝 Contributing

1. Clone repository
2. Tạo branch mới: `git checkout -b feature/your-feature`
3. Commit changes: `git commit -m "Add feature"`
4. Push: `git push origin feature/your-feature`
5. Tạo Pull Request

---

## 📞 Support

- **Issues**: [GitHub Issues](#)
- **Docs**: [Confluence](#)
- **Chat**: [Slack #samt-project](#)
