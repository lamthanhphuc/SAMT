# ==============================================
# Multi-Stage Build for Report Service
# ==============================================

FROM eclipse-temurin:21.0.6_7-jre-alpine@sha256:4e9ab608d97796571b1d5bbcd1c9f430a89a5f03fe5aa6c093888ceb6756c502
RUN addgroup -S app && adduser -S -G app -h /app app
WORKDIR /app
COPY --chown=app:app services/report-service/target/*.jar /app/app.jar
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
	CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1
USER app
ENTRYPOINT ["java","-jar","/app/app.jar"]
