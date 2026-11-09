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

# 統合テスト層。DB コンテナと同じネットワークで実行時はオフラインで走らせるため、
# キャッシュマウントで解決した依存と Gradle 本体を層へ写す。接続先が無いので全クラス skip されるが、
# test タスクが要る依存（JaCoCo エージェント等）はここで解決される。
FROM dependencies AS integration-test
WORKDIR /app
COPY src src
RUN --mount=type=cache,id=gradle-cache,target=/gradle-cache,uid=1000,gid=1000,sharing=locked \
    GRADLE_USER_HOME=/gradle-cache ./gradlew test --tests "*IntegrationTest" --no-daemon \
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

# シェルが無いので $JAVA_OPTS を展開できない。JVM 自身が読む JAVA_TOOL_OPTIONS で渡す（運用の調整は後に読まれる JDK_JAVA_OPTIONS で上書きする）。
# ヒープはコンテナのメモリ上限に対する割合で決め、上限だけで大きさが追従するようにする（既定の上限 1GiB で 256MiB〜512MiB）。
# 残りはメタスペース・スレッド・tmpfs の添付一時ファイルに回る。G1 を明示するのは、2GiB 未満の上限だと JVM が SerialGC を選ぶため
ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=25 -XX:MaxRAMPercentage=50 -XX:+UseG1GC"

COPY --from=build /app/build/libs/kizuna-*.jar app.jar
COPY --from=build /app/healthcheck healthcheck
# log4j2 の既定出力先 ./logs を非 root（65532）で書けるようにする
COPY --from=build --chown=65532:65532 /app/logs logs
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
