FROM dhi.io/gradle:9-jdk25-alpine-dev AS dependencies
# DHI の gradle イメージは root（HOME=/root）で動くため、キャッシュマウント先へ明示的に向ける
ENV GRADLE_USER_HOME=/home/gradle/.gradle
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradlew gradlew
COPY gradle gradle
RUN chmod +x gradlew
RUN --mount=type=cache,id=gradle-cache,target=/home/gradle/.gradle,uid=1000,gid=1000,sharing=locked \
    ./gradlew dependencies --configuration runtimeClasspath --no-daemon

FROM dependencies AS lint
WORKDIR /app
COPY src src
RUN --mount=type=cache,id=gradle-cache,target=/home/gradle/.gradle,uid=1000,gid=1000,sharing=locked \
    ./gradlew --no-daemon --info spotlessCheck

FROM dependencies AS test
WORKDIR /app
COPY src src
RUN --mount=type=cache,id=gradle-cache,target=/home/gradle/.gradle,uid=1000,gid=1000,sharing=locked \
    ./gradlew clean test jacocoTestReport jacocoTestCoverageVerification --no-daemon --info

# 実 PostgreSQL テスト層。DB コンテナと同じネットワークで docker run して実行時はオフラインで走らせるため、
# キャッシュマウントで解決した依存と Gradle 本体を層へ写す。
FROM dependencies AS postgres-test
WORKDIR /app
COPY src src
RUN --mount=type=cache,id=gradle-cache,target=/gradle-cache,uid=1000,gid=1000,sharing=locked \
    GRADLE_USER_HOME=/gradle-cache ./gradlew testClasses --no-daemon \
    && mkdir -p /home/gradle/.gradle/caches \
    && cp -a /gradle-cache/wrapper /home/gradle/.gradle/ \
    && cp -a /gradle-cache/caches/modules-2 /home/gradle/.gradle/caches/

FROM dependencies AS build
WORKDIR /app
COPY src src
RUN --mount=type=cache,id=gradle-cache,target=/home/gradle/.gradle,uid=1000,gid=1000,sharing=locked \
    ./gradlew build --no-daemon --info -x test
COPY docker/HealthCheck.java docker/
RUN javac -d healthcheck docker/HealthCheck.java && mkdir logs

FROM dhi.io/eclipse-temurin:25-alpine3.23
WORKDIR /app

# シェルが無いので $JAVA_OPTS を展開できない。JVM 自身が読む JAVA_TOOL_OPTIONS で渡す
ARG APP_JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport"
ENV JAVA_TOOL_OPTIONS=${APP_JAVA_OPTS}

COPY --from=build /app/build/libs/kizuna-*.jar app.jar
COPY --from=build /app/healthcheck healthcheck
# log4j2 の既定出力先 ./logs を非 root（65532）で書けるようにする
COPY --from=build --chown=65532:65532 /app/logs logs
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
