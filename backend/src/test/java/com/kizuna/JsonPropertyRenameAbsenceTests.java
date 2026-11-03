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
 * ワイヤ名の写像源が命名戦略ひとつであることを機械検証する。{@code CommonExceptionHandler} は details のキーを命名戦略だけで組み立てるため、
 * {@code @JsonProperty} で別名を与えた項目は実際の JSON と details のキーが静かに食い違う。名前を変えない用法（{@code required =
 * true}）は許し、名前を与える用法（{@code @JsonProperty("x")} / {@code value = "x"}）だけを拒む。行内 FQCN とワイルドカード import
 * を使わない規約が、import での絞り込みの前提である。
 */
class JsonPropertyRenameAbsenceTests {

  private static final Path MAIN_SOURCES = Paths.get("src/main/java");

  private static final String JSON_PROPERTY_IMPORT =
      "import com.fasterxml.jackson.annotation.JsonProperty;";

  private static final Pattern RENAMING =
      Pattern.compile("@JsonProperty\\([^)]*(\"|\\bvalue\\s*=)");

  @Test
  @DisplayName("src/main/java に @JsonProperty で別名を与える用法が無いこと")
  void mainSourcesRenameNoProperty() throws Exception {
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
      String source = Files.readString(file);
      if (source.contains(JSON_PROPERTY_IMPORT) && RENAMING.matcher(source).find()) {
        offenders.add(MAIN_SOURCES.relativize(file).toString());
      }
    }

    // 暗黙の no-op 防止: 走査が実際にソースを読めていることを担保する。
    assertThat(javaFiles).as("src/main/java 配下の .java ソース").isNotEmpty();

    assertThat(offenders).as("@JsonProperty で別名を与えているソース").isEmpty();
  }

  @Test
  @DisplayName("別名の検出式は名前を与える用法だけに当たる")
  void renamingPatternMatchesOnlyNamingUsages() {
    assertThat(RENAMING.matcher("@JsonProperty(\"x\") String a").find()).isTrue();
    assertThat(RENAMING.matcher("@JsonProperty(value = \"x\") String a").find()).isTrue();
    assertThat(RENAMING.matcher("@JsonProperty(required = true, value = \"x\") String a").find())
        .isTrue();
    assertThat(RENAMING.matcher("@JsonProperty(required = true) String a").find()).isFalse();
  }
}
