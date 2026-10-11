# 1단계: Gradle Wrapper로 실행 가능한 jar를 만든다. 테스트는 CI에서 따로 돌리므로 여기서는 건너뛴다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon --quiet dependencies > /dev/null 2>&1 || true
COPY src ./src
RUN ./gradlew --no-daemon --quiet bootJar -x test

# 2단계: JRE만 있는 작은 이미지. curl은 compose와 K8s healthcheck용이다.
# 사용자는 숫자 UID로 지정한다. 쿠버네티스의 runAsNonRoot는 이미지의 USER가 이름(app)이면 비루트인지 검증하지 못해
# CreateContainerConfigError를 낸다(kind 실측, docs/evaluation.md).
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
