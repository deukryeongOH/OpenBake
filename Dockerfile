# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY common/build.gradle common/build.gradle
COPY member-service/build.gradle member-service/build.gradle
COPY payment-service/build.gradle payment-service/build.gradle
RUN chmod +x gradlew && ./gradlew --version

COPY src ./src
COPY common/src ./common/src
COPY payment-service/src ./payment-service/src
RUN ./gradlew :bootJar -x test --no-daemon

# ---- Run stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

ENV TZ=Asia/Seoul
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=builder /app/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
