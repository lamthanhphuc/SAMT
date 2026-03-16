# ==============================================
# Multi-Stage Build for API Gateway
# ==============================================

FROM eclipse-temurin:21.0.6_7-jre-alpine@sha256:4e9ab608d97796571b1d5bbcd1c9f430a89a5f03fe5aa6c093888ceb6756c502

# Create an unprivileged user/group for the runtime container
RUN addgroup -S app && adduser -S -G app -h /app app

WORKDIR /app
COPY --chown=app:app services/api-gateway/target/*.jar /app/app.jar

# Minimal container-level healthcheck (can be replaced by orchestration probes)
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
	CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1

USER app
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]
