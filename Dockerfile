# Build stage: the boot jar, built with the Gradle wrapper. Tests run in ./gradlew check, not here.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon --quiet dependencies > /dev/null
COPY src/main src/main
RUN ./gradlew --no-daemon --quiet bootJar && cp build/libs/url-shortener-*.jar app.jar

# Runtime stage: distroless Java 21, no shell or package manager, non-root (R33, S-15).
# Compose adds a read-only root filesystem with a tmpfs at /tmp.
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER nonroot
# 8080 serves the API and redirects. 8081 (actuator) stays inside the container network (R31).
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
