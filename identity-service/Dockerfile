# ==============================================
# Multi-Stage Build for Identity Service
# ==============================================
# Stage 1: Build với Maven (nếu cần build trong Docker)
# Stage 2: Runtime với JRE nhỏ gọn

# Stage 1: Builder (optional - dùng nếu build trong Docker)
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
# Copy only dependencies first (layer caching)
COPY pom.xml ./
COPY ../pom.xml ../pom.xml
# Download dependencies (cached nếu pom.xml không đổi)
RUN mvn dependency:go-offline || true
# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime (lightweight)
FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="samt-team"
LABEL service="identity-service"

# Security: Run as non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

WORKDIR /app

# Copy JAR from builder or target (use ARG for flexibility)
ARG JAR_FILE=target/*.jar
COPY --chown=spring:spring ${JAR_FILE} app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM Options từ environment variable
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Entrypoint với dynamic JAVA_OPTS
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
