# --- Stage 1: Build all modules from the root ---
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy the parent POM and all module directories
COPY pom.xml .
COPY openweather-core ./openweather-core
COPY openweather-adapter ./openweather-adapter
COPY weather-spring-boot-starter ./weather-spring-boot-starter
COPY weather-web-api ./weather-web-api
COPY rate-limit-starter ./rate-limit-starter

# Compile and package everything from the root
RUN mvn clean package -DskipTests

# --- Stage 2: Extract and run ONLY the Web API ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the built fat JAR directly out of the web-api's target folder
COPY --from=builder /app/weather-web-api/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
