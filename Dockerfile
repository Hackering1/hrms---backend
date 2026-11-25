FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY src src
RUN chmod +x mvnw && ./mvnw -B clean package -DskipTests -Duser.timezone=Asia/Kolkata

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/hrms-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Kolkata", "-jar", "app.jar"]
