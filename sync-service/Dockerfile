FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

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

RUN mvn -B -q -e -DskipTests -pl sync-service -am dependency:go-offline

COPY . .

RUN mvn -B -DskipTests -pl sync-service -am clean package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /build/sync-service/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
