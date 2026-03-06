# ==============================================
# Multi-Stage Build for Notification Service
# ==============================================

# Stage 1: Builder
FROM maven:3.9.6-eclipse-temurin-21@sha256:8d63d4c1902cb12d9e79a70671b18ebe26358cb592561af33ca1808f00d935cb AS builder
WORKDIR /app

COPY pom.xml .
COPY common-contracts/pom.xml common-contracts/pom.xml
COPY common-events/pom.xml common-events/pom.xml
COPY analysis-service/pom.xml analysis-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY identity-service/pom.xml identity-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY project-config-service/pom.xml project-config-service/pom.xml
COPY report-service/pom.xml report-service/pom.xml
COPY sync-service/pom.xml sync-service/pom.xml
COPY user-group-service/pom.xml user-group-service/pom.xml

RUN mvn -B -q -e -DskipTests -pl notification-service -am dependency:go-offline

COPY . .

RUN mvn -B -DskipTests -pl notification-service -am clean package

# Stage 2: Runtime (hardened)
FROM eclipse-temurin:21.0.6_7-jre-alpine@sha256:4e9ab608d97796571b1d5bbcd1c9f430a89a5f03fe5aa6c093888ceb6756c502
RUN addgroup -S app && adduser -S -G app -h /app app
WORKDIR /app
COPY --from=builder --chown=app:app /app/notification-service/target/*.jar /app/app.jar
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
	CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1
USER app
ENTRYPOINT ["java","-jar","/app/app.jar"]
