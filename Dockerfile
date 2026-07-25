# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version

COPY src ./src
RUN ./gradlew clean bootJar -x test --no-daemon

# ---- Run stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
