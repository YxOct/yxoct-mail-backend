FROM maven:3.9.11-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package \
    && cp target/yxoct-mail-backend-*.jar /workspace/application.jar

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app \
    && adduser -S -G app -h /app app

WORKDIR /app

COPY --from=build --chown=app:app /workspace/application.jar ./application.jar

USER app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health/readiness" >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
