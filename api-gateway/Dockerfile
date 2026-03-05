# ==============================================
# Multi-Stage Build for API Gateway
# ==============================================

# Stage 1: Builder (optional)
FROM maven:3.9.6-eclipse-temurin-21@sha256:8d63d4c1902cb12d9e79a70671b18ebe26358cb592561af33ca1808f00d935cb AS builder
WORKDIR /app

COPY pom.xml .
COPY analysis-service/pom.xml analysis-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY common-events/pom.xml common-events/pom.xml
COPY identity-service/pom.xml identity-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY project-config-service/pom.xml project-config-service/pom.xml
COPY report-service/pom.xml report-service/pom.xml
COPY sync-service/pom.xml sync-service/pom.xml
COPY user-group-service/pom.xml user-group-service/pom.xml

RUN mvn -B -q -e -DskipTests dependency:go-offline -pl api-gateway -am

COPY analysis-service/src analysis-service/src
COPY api-gateway/src api-gateway/src
COPY common-events/src common-events/src
COPY identity-service/src identity-service/src
COPY notification-service/src notification-service/src
COPY project-config-service/src project-config-service/src
COPY report-service/src report-service/src
COPY sync-service/src sync-service/src
COPY user-group-service/src user-group-service/src

RUN mvn -B clean package -DskipTests -pl api-gateway -am

# Stage 2: Runtime (hardened)
FROM eclipse-temurin:21.0.6_7-jre-alpine

# Create an unprivileged user/group for the runtime container
RUN addgroup -S app && adduser -S -G app -h /app app

WORKDIR /app
COPY --from=builder --chown=app:app /app/api-gateway/target/*.jar /app/app.jar

# Minimal container-level healthcheck (can be replaced by orchestration probes)
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
	CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1

USER app
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]
