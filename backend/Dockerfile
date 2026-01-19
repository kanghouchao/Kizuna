# 1. Download and cache dependencies
FROM gradle:9.2.1-jdk21-ubi-minimal AS dependencies
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradlew gradlew
COPY gradle gradle
RUN chmod +x gradlew
RUN --mount=type=cache,id=gradle-cache,target=/home/gradle/.gradle,uid=1000,gid=1000,sharing=locked \
    ./gradlew dependencies --configuration runtimeClasspath --no-daemon

# Lint stage: run Spotless check
FROM dependencies AS lint
WORKDIR /app
COPY src src
RUN --mount=type=cache,id=gradle-cache,target=/home/gradle/.gradle,uid=1000,gid=1000,sharing=locked \
    ./gradlew --no-daemon --info spotlessCheck

# 2. Run tests
FROM dependencies AS test
WORKDIR /app
COPY src src
RUN --mount=type=cache,id=gradle-cache,target=/home/gradle/.gradle,uid=1000,gid=1000,sharing=locked \
    ./gradlew clean test jacocoTestReport jacocoTestCoverageVerification --no-daemon --info

# 3. Build artifacts
FROM dependencies AS build
WORKDIR /app
COPY src src
RUN --mount=type=cache,id=gradle-cache,target=/home/gradle/.gradle,uid=1000,gid=1000,sharing=locked \
    ./gradlew build --no-daemon --info -x test

# 4. Create runtime image
FROM eclipse-temurin:21-jre-alpine-3.22
WORKDIR /app

ARG APP_JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport"
ENV JAVA_OPTS=${APP_JAVA_OPTS}

# Install curl used by the healthcheck
RUN apk add --no-cache curl

COPY --from=build /app/build/libs/cms-*.jar app.jar
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
