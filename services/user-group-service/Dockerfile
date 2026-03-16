FROM eclipse-temurin:21-jre

WORKDIR /app

COPY services/user-group-service/target/*.jar /app/app.jar

EXPOSE 8082

ENTRYPOINT ["java","-jar","/app/app.jar"]
