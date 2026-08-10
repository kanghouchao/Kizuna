package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ワイヤ名の写像源が命名戦略ひとつであることを機械検証する。
 *
 * <p>{@code CommonExceptionHandler} は検証エラーの details のキーを、Bean のプロパティパスに {@code
 * spring.jackson.property-naming-strategy} と同一の実装を適用して組み立てる。個別に {@code @JsonProperty}
 * で別名を与えた項目があると、その項目だけ実際の JSON と details のキーが食い違い、前端がフィールドを引けなくなる — 型でも実行時でも検出されない静默の食い違いなので、
 * {@link PermissionLiteralTests} と同型の fitness test で注解の不在そのものを固定する。
 *
 * <p>検出は {@code @JsonProperty} の import 宣言で行う。裸の字面照合では javadoc 中の言及（{@code CommonExceptionHandler}
 * のワイヤ名解説）を誤検知するため。この方式が成立する前提は 2 つとも本倉のコード規約である — 行内 FQCN を書かないこと、 ワイルドカード import
 * を使わないこと。どちらかが崩れると、この検査は注解の使用を見落とす。
 */
class JsonPropertyAbsenceTests {

  private static final Path MAIN_SOURCES = Paths.get("src/main/java");

  private static final String JSON_PROPERTY_IMPORT =
      "import com.fasterxml.jackson.annotation.JsonProperty;";

  @Test
  @DisplayName("src/main/java に @JsonProperty の使用が無いこと")
  void mainSourcesDeclareNoJsonProperty() throws Exception {
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
      if (Files.readString(file).contains(JSON_PROPERTY_IMPORT)) {
        offenders.add(MAIN_SOURCES.relativize(file).toString());
      }
    }

    // 暗黙の no-op 防止: 走査が実際にソースを読めていることを担保する。
    assertThat(javaFiles).as("src/main/java 配下の .java ソース").isNotEmpty();

    assertThat(offenders).as("@JsonProperty を import しているソース").isEmpty();
  }
}
