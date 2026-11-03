package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 主コードが「今」を読む口を二つに限る。行に書く時刻は {@code Timestamps.now()}、期限・本日の判定は業務の時計 {@code now(clock)}。
 *
 * <p>JVM の時計を直に読むと、ナノ秒が保存時に四捨五入されるか、固定時計の試験で判定を動かせなくなる。
 */
class TimestampSourceTests {

  private static final Path MAIN_SOURCES = Paths.get("src/main/java");

  // 静的 import は呼び出し側に型名が残らないため、import 宣言そのものを拒否する。
  private static final Pattern UNBOUNDED_NOW =
      Pattern.compile(
          "\\b(?:Instant|OffsetDateTime|OffsetTime|ZonedDateTime|LocalDateTime|LocalDate|LocalTime"
              + "|YearMonth|Year|MonthDay)\\.now\\((?!clock\\))"
              + "|\\bClock\\.system\\w*\\("
              + "|System\\.currentTimeMillis\\(\\)"
              + "|import static java\\.time\\.\\w+\\.(?:now|system\\w*|\\*);");

  // 時刻源そのものと、業務判定でも応答に出る時刻でもない JVM 時計の読み手。読む箇所の数まで固定し、
  // 許可したファイルへ新たな読み手が加わっても検出する。
  private record Allowance(int reads, String reason) {}

  private static final Map<Path, Allowance> ALLOWED =
      Map.of(
          Paths.get("com/kizuna/shared/persistence/Timestamps.java"),
              new Allowance(1, "行に書く時刻の時刻源"),
          Paths.get("com/kizuna/shared/config/ClockConfig.java"), new Allowance(1, "業務の時計の時刻源"),
          Paths.get("com/kizuna/auth/infrastructure/JwtIssuer.java"),
              new Allowance(
                  1, "JWT の iat/exp。検証側の JwtTimestampValidator は setClock せず既定の時計で読むので揃える"),
          Paths.get("com/kizuna/auth/infrastructure/SessionTokenDecoder.java"),
              new Allowance(1, "refresh JWT の有効期間の検証。発行側と同じ JVM の時計で読む"),
          Paths.get("com/kizuna/auth/infrastructure/RefreshTokenCookies.java"),
              new Allowance(1, "refresh cookie の Max-Age。JWT の exp までの残り秒"),
          Paths.get("com/kizuna/auth/infrastructure/TokenBlacklistService.java"),
              new Allowance(1, "失効キーの TTL。JWT の exp までの残りミリ秒"),
          Paths.get("com/kizuna/shared/persistence/SnowflakeIdGenerator.java"),
              new Allowance(3, "ID の採番に埋める epoch ミリ秒。時刻としては保存も応答もしない"));

  // 期限・有効期間の判定を名乗るドメインのメソッド。判定の時刻を引数で受け、自分では「今」を読まない。
  private static final Pattern DOMAIN_JUDGEMENT =
      Pattern.compile("boolean\\s+(isExpired|isWithin\\w*|is\\w+At|is\\w+On)\\s*\\(([^)]*)\\)");

  // 行に書く時刻で期限を判じる呼び出し。引数のどこにあっても拾う（入れ子の括弧は一段まで）。
  private static final Pattern JUDGED_BY_RECORD_TIME =
      Pattern.compile(
          "\\b(?:isExpired|isWithin\\w*|is\\w+At|is\\w+On)\\((?:[^()]|\\([^()]*\\))*?Timestamps\\.now\\(\\)");

  private static final Pattern TIME_PARAMETER =
      Pattern.compile(
          "\\b(?:OffsetDateTime|Instant|LocalDate|LocalDateTime|ZonedDateTime)\\s+\\w+");

  @Test
  @DisplayName("src/main/java が JVM の時計を読むのは、時刻源と理由付きの許可に限られること")
  void mainSourcesTakeNowFromTheTwoSources() throws Exception {
    List<Path> javaFiles = mainSources();

    List<String> offenders = new ArrayList<>();
    for (Path file : javaFiles) {
      var relative = MAIN_SOURCES.relativize(file);
      var allowance = ALLOWED.get(relative);
      long reads = UNBOUNDED_NOW.matcher(Files.readString(file)).results().count();
      if (reads > (allowance == null ? 0 : allowance.reads())) {
        offenders.add(relative + "（" + reads + " 箇所）");
      }
    }

    // 暗黙の no-op 防止: 走査が実際にソースを読めており、許可した箇所が今もその数だけ JVM の時計を読んでいることを担保する。
    assertThat(javaFiles).as("src/main/java 配下の .java ソース").isNotEmpty();
    for (var allowed : ALLOWED.entrySet()) {
      var source = Files.readString(MAIN_SOURCES.resolve(allowed.getKey()));
      assertThat(UNBOUNDED_NOW.matcher(source).results())
          .as(
              "許可した %s が今も %d 箇所で JVM の時計を読むこと（増減したら許可を見直す）",
              allowed.getKey(), allowed.getValue().reads())
          .hasSize(allowed.getValue().reads());
    }

    assertThat(offenders).as("JVM の時計を時刻源の外で、または許可した数を超えて読むソース").isEmpty();
  }

  @Test
  @DisplayName("ドメインの期限判定メソッドは判定の時刻を引数で受けること")
  void domainJudgementsTakeTheInstantAsAnArgument() throws Exception {
    List<String> judgements = new ArrayList<>();
    List<String> offenders = new ArrayList<>();
    for (Path file : mainSources()) {
      var relative = MAIN_SOURCES.relativize(file);
      if (!relative.toString().contains("/domain/")) {
        continue;
      }
      var matcher = DOMAIN_JUDGEMENT.matcher(Files.readString(file));
      while (matcher.find()) {
        var method = relative.getFileName() + "#" + matcher.group(1);
        judgements.add(method);
        if (!TIME_PARAMETER.matcher(matcher.group(2)).find()) {
          offenders.add(method);
        }
      }
    }

    assertThat(judgements).as("走査が既知の期限判定を拾うこと").contains("CastInvitation.java#isExpired");
    assertThat(offenders).as("判定の時刻を引数で受けない期限判定").isEmpty();
  }

  @Test
  @DisplayName("期限判定に行に書く時刻（Timestamps.now()）を渡さないこと")
  void judgementsDoNotReadTheRecordTime() throws Exception {
    List<String> offenders = new ArrayList<>();
    for (Path file : mainSources()) {
      if (JUDGED_BY_RECORD_TIME.matcher(Files.readString(file)).find()) {
        offenders.add(MAIN_SOURCES.relativize(file).toString());
      }
    }

    assertThat(offenders).as("期限判定に Timestamps.now() を渡すソース").isEmpty();
  }

  private static List<Path> mainSources() throws Exception {
    try (Stream<Path> paths = Files.walk(MAIN_SOURCES)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".java"))
          .toList();
    }
  }
}
