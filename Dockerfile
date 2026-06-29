# --- Stage 1: Build the Maven application ---
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the pom.xml and dependencies configuration first (helps Docker cache dependencies layers)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the source code and compile the JAR package
COPY src ./src
RUN mvn clean package -DskipTests

# --- Stage 2: Create the runtime container ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the packaged JAR from the build stage
COPY --from=build /app/target/iot-sensor-aggregator-1.0.0.jar app.jar

# Dynamic Port routing for Render/Railway deployments
ENV PORT=8080
EXPOSE 8080

# Execute the Spring Boot jar
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
