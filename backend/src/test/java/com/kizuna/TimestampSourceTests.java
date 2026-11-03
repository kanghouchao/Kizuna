package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * アプリが作る {@code OffsetDateTime} の「今」は {@code Timestamps.now()} か業務の時計 {@code now(clock)} を通す。
 *
 * <p>それ以外の {@code OffsetDateTime.now(...)} はナノ秒を持ち込み、保存時の四捨五入で応答と読み直しが食い違う。Mac の JDK の時計はマイクロ秒なので
 * Linux でしか再現しない。
 */
class TimestampSourceTests {

  private static final Path MAIN_SOURCES = Paths.get("src/main/java");

  private static final Path TIMESTAMPS = Paths.get("com/kizuna/shared/persistence/Timestamps.java");

  // 静的 import は呼び出し側に型名が残らないため、import 宣言そのものを拒否する。
  private static final Pattern UNBOUNDED_NOW =
      Pattern.compile(
          "OffsetDateTime\\.now\\((?!clock\\))|import static java\\.time\\.OffsetDateTime\\.");

  @Test
  @DisplayName("src/main/java の OffsetDateTime.now は Timestamps と業務の時計に限られること")
  void mainSourcesTakeNowFromTheTwoSources() throws Exception {
    List<Path> javaFiles;
    try (Stream<Path> paths = Files.walk(MAIN_SOURCES)) {
      javaFiles =
          paths
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".java"))
              .toList();
    }

    List<String> offenders = new ArrayList<>();
    for (Path file : javaFiles) {
      var relative = MAIN_SOURCES.relativize(file);
      if (!relative.equals(TIMESTAMPS) && UNBOUNDED_NOW.matcher(Files.readString(file)).find()) {
        offenders.add(relative.toString());
      }
    }

    // 暗黙の no-op 防止: 走査が実際にソースを読めており、除外した時刻源が実在することを担保する。
    assertThat(javaFiles).as("src/main/java 配下の .java ソース").isNotEmpty();
    assertThat(MAIN_SOURCES.resolve(TIMESTAMPS)).as("時刻源").exists();

    assertThat(offenders).as("OffsetDateTime.now を時刻源の外で呼ぶソース").isEmpty();
  }
}
