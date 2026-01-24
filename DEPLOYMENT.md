# ==============================================
# DEPLOYMENT GUIDE - SAMT MICROSERVICES
# ==============================================

## 📋 PRE-DEPLOYMENT CHECKLIST

### 1. Bảo mật
- [ ] Đổi `POSTGRES_PASSWORD` trong `.env`
- [ ] Đặt Redis password
- [ ] Cấu hình SSL/TLS cho databases
- [ ] Review exposed ports
- [ ] Kiểm tra firewall rules

### 2. Infrastructure
- [ ] Docker 20.10+ installed
- [ ] Docker Compose 2.0+ installed
- [ ] Minimum 4GB RAM available
- [ ] Minimum 10GB disk space

### 3. Configuration
- [ ] Copy `.env.example` → `.env`
- [ ] Update all credentials in `.env`
- [ ] Review resource limits in docker-compose.yml

---

## 🚀 DEPLOYMENT STEPS

### Option 1: Full Stack Deployment (Docker)

```powershell
# 1. Clone repository
git clone <repository-url>
cd SAMT

# 2. Configure environment
cp .env.example .env
# Edit .env với credentials thật

# 3. Build tất cả services
./mvnw clean package -DskipTests

# 4. Start infrastructure first
docker-compose up -d postgres-identity postgres-core redis

# 5. Wait for databases to be ready (30 seconds)
Start-Sleep -Seconds 30

# 6. Start application services
docker-compose up -d

# 7. Verify deployment
docker-compose ps
docker-compose logs -f

# 8. Health check
curl http://localhost:9080/actuator/health
curl http://localhost:8081/actuator/health
```

### Option 2: Databases Only (IDE Development)

```powershell
# 1. Start only databases
docker-compose up -d postgres-identity postgres-core redis

# 2. Verify
docker ps

# 3. Run services from IntelliJ/Eclipse
# - identity-service: port 8081
# - sync-service: port 8082
# - analysis-service: port 8083
# - report-service: port 8084
# - notification-service: port 8085
# - api-gateway: port 9080 (với profile "local")
```

---

## 🔍 VERIFICATION

### 1. Kiểm tra containers
```powershell
docker-compose ps
# Tất cả services phải có status "Up" và "healthy"
```

### 2. Kiểm tra logs
```powershell
# Logs tất cả services
docker-compose logs -f

# Logs 1 service cụ thể
docker-compose logs -f identity-service
docker-compose logs -f api-gateway
```

### 3. Health checks
```powershell
# API Gateway
curl http://localhost:9080/actuator/health

# Identity Service
curl http://localhost:8081/actuator/health

# Sync Service
curl http://localhost:8082/actuator/health

# Analysis Service
curl http://localhost:8083/actuator/health

# Report Service
curl http://localhost:8084/actuator/health

# Notification Service
curl http://localhost:8085/actuator/health
```

### 4. Database connectivity
```powershell
# PostgreSQL Identity
docker exec -it postgres-identity psql -U postgres -d samt_identity -c "SELECT version();"

# PostgreSQL Core
docker exec -it postgres-core psql -U postgres -d samt_core -c "SELECT version();"

# Redis
docker exec -it redis redis-cli PING
```

---

## 🛠️ TROUBLESHOOTING

### Issue: Container fails to start

```powershell
# Xem logs chi tiết
docker-compose logs <service-name>

# Restart service
docker-compose restart <service-name>

# Rebuild và restart
docker-compose up -d --build <service-name>
```

### Issue: Database connection refused

```powershell
# Kiểm tra database đã ready chưa
docker-compose ps postgres-identity postgres-core

# Xem logs database
docker-compose logs postgres-identity
docker-compose logs postgres-core

# Kiểm tra network
docker network inspect samt_samt-network
```

### Issue: Port already in use

```powershell
# Kiểm tra port đang bị chiếm
netstat -ano | findstr :5432
netstat -ano | findstr :5433
netstat -ano | findstr :6379

# Kill process hoặc đổi port trong .env
```

### Issue: Out of memory

```powershell
# Tăng resource limits trong docker-compose.yml
# Hoặc giảm JAVA_OPTS memory settings
```

---

## 📊 MONITORING

### Logs
```powershell
# Real-time logs
docker-compose logs -f

# Logs từ 10 phút trước
docker-compose logs --since 10m

# Logs với timestamp
docker-compose logs -t
```

### Metrics
```powershell
# Container stats
docker stats

# Service-specific metrics
curl http://localhost:8081/actuator/metrics
curl http://localhost:8081/actuator/metrics/jvm.memory.used
```

### Disk usage
```powershell
# Docker disk usage
docker system df

# Clean unused data
docker system prune -a
```

---

## 🔄 UPDATES & MAINTENANCE

### Update application code
```powershell
# 1. Pull latest code
git pull

# 2. Rebuild JAR
./mvnw clean package -DskipTests

# 3. Rebuild Docker image
docker-compose build <service-name>

# 4. Restart service
docker-compose up -d <service-name>
```

### Update single service
```powershell
# Zero-downtime update (nếu có replica)
docker-compose up -d --no-deps --build <service-name>
```

### Database backup
```powershell
# Backup PostgreSQL Identity
docker exec postgres-identity pg_dump -U postgres samt_identity > backup_identity_$(Get-Date -Format 'yyyyMMdd').sql

# Backup PostgreSQL Core
docker exec postgres-core pg_dump -U postgres samt_core > backup_core_$(Get-Date -Format 'yyyyMMdd').sql

# Backup Redis
docker exec redis redis-cli SAVE
docker cp redis:/data/dump.rdb ./backup_redis_$(Get-Date -Format 'yyyyMMdd').rdb
```

---

## 🛑 SHUTDOWN

### Graceful shutdown
```powershell
# Stop tất cả services (giữ volumes)
docker-compose down

# Stop và xoá volumes (CAREFUL!)
docker-compose down -v
```

### Emergency shutdown
```powershell
# Force stop
docker-compose kill
```

---

## 🔐 PRODUCTION RECOMMENDATIONS

1. **Security**
   - Sử dụng Docker secrets thay vì environment variables
   - Enable SSL/TLS cho PostgreSQL
   - Set Redis password
   - Giới hạn network exposure

2. **Scalability**
   - Deploy với Docker Swarm hoặc Kubernetes
   - Sử dụng load balancer
   - Scale services horizontally

3. **Monitoring**
   - Integrate với Prometheus + Grafana
   - Setup alerting (PagerDuty, Slack)
   - Centralized logging (ELK stack)

4. **Backup**
   - Automated daily backups
   - Off-site backup storage
   - Test restore procedures

5. **High Availability**
   - PostgreSQL replication
   - Redis Sentinel/Cluster
   - Multi-node deployment

---

## 📞 SUPPORT

- **Documentation**: README.md
- **Issues**: GitHub Issues
- **Emergency**: <on-call-contact>
