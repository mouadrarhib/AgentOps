# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
# Integration tests require PostgreSQL; run the test suite separately.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -DskipTests package \
    && cp target/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN groupadd --gid 10001 agentops \
    && useradd --uid 10001 --gid agentops --no-create-home --shell /usr/sbin/nologin agentops
COPY --from=build --chown=agentops:agentops /workspace/app.jar ./app.jar

USER agentops:agentops
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
