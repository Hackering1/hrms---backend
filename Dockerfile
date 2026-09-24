FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn clean package -DskipTests -Duser.timezone=Asia/Kolkata

FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY --from=build /app/target/hrms-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Kolkata", "-jar", "app.jar"]
