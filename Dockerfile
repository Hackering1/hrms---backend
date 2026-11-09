FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY hrms/mvnw .
COPY hrms/.mvn .mvn
COPY hrms/pom.xml .
COPY hrms/src src
RUN chmod +x mvnw && ./mvnw clean package -DskipTests -Duser.timezone=Asia/Kolkata
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Kolkata", "-jar", "target/hrms-0.0.1-SNAPSHOT.jar"]
