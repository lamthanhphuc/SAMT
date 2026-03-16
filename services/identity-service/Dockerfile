FROM eclipse-temurin:21-jre

WORKDIR /app

COPY services/identity-service/target/*.jar /app/app.jar

EXPOSE 8081

ENTRYPOINT ["java","-jar","/app/app.jar"]
