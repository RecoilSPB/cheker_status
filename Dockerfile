# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp \
        -Dmaven.wagon.http.retryHandler.count=5 \
        -Dmaven.wagon.rto=180000 \
        -Daether.connector.connectTimeout=60000 \
        -Daether.connector.requestTimeout=180000 \
        dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests \
        -Dmaven.wagon.http.retryHandler.count=5 \
        -Dmaven.wagon.rto=180000 \
        -Daether.connector.connectTimeout=60000 \
        -Daether.connector.requestTimeout=180000 \
        package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/target/cheker-status-0.0.1-SNAPSHOT.jar /app/cheker-status.jar
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
USER app

ENV JAVA_OPTS=""
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/cheker-status.jar"]
