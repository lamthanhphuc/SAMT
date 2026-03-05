# ==============================================
# Multi-Stage Build for User Group Service
# ==============================================

# Stage 1: Builder
FROM maven:3.9.6-eclipse-temurin-21 AS builder
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

RUN mvn -B -q -e -DskipTests dependency:go-offline -pl user-group-service -am

COPY analysis-service/src analysis-service/src
COPY api-gateway/src api-gateway/src
COPY common-events/src common-events/src
COPY identity-service/src identity-service/src
COPY notification-service/src notification-service/src
COPY project-config-service/src project-config-service/src
COPY report-service/src report-service/src
COPY sync-service/src sync-service/src
COPY user-group-service/src user-group-service/src

RUN mvn -B clean package -DskipTests -pl user-group-service -am

FROM eclipse-temurin:21.0.6_7-jre-alpine
RUN addgroup -S app && adduser -S -G app -h /app app
WORKDIR /app
COPY --from=builder --chown=app:app /app/user-group-service/target/*.jar /app/app.jar
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
	CMD wget -q --spider http://127.0.0.1:8082/actuator/health || exit 1
USER app
ENTRYPOINT ["java","-jar","/app/app.jar"]
