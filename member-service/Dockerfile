# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY common/build.gradle common/build.gradle
# settings.gradle이 include하는 모든 모듈의 build.gradle이 있어야 한다.
# 하나라도 빠지면 "projectDirectory does not exist"로 설정 단계에서 실패한다.
COPY common-logging/build.gradle common-logging/build.gradle
COPY member-service/build.gradle member-service/build.gradle
COPY payment-service/build.gradle payment-service/build.gradle
COPY api-gateway/build.gradle api-gateway/build.gradle
COPY ai-service/build.gradle ai-service/build.gradle
RUN chmod +x gradlew && ./gradlew --version

COPY common-logging/src ./common-logging/src
COPY common/src ./common/src
COPY member-service/src ./member-service/src
RUN ./gradlew :member-service:bootJar -x test --no-daemon

# ---- Run stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

ENV TZ=Asia/Seoul
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=builder /app/member-service/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8081

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
