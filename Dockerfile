# Build stage (use jammy/Ubuntu base - Alpine has no arm64 image for Temurin)
FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source and build
COPY src src

# Build JAR (skip tests for faster builds)
RUN ./gradlew bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create non-root user (Debian/Ubuntu use groupadd/useradd)
RUN groupadd -r app && useradd -r -g app app

# Copy JAR from build stage (firebase_config.json is bundled in JAR from src/main/resources)
COPY --from=build /app/build/libs/*.jar app.jar
RUN chown app:app app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
