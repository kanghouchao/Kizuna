package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.exception.DbConstraint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DbConstraint} の制約名字面と changelog の実 DDL の整合を機械検証する。
 *
 * <p>制約名は Java 側では単なる文字列で、DDL とのコンパイル期関連が一切ない。綴りの誤りや DDL 側の改名は「決して命中しない写像」を静默に生む — 変換されるはずの整合性違反が
 * 全域ハンドラの兜底へ落ち、利用者には 500 や汎用文言として現れる。{@link PermissionLiteralTests} と同型の fitness test でこの欠陥類を閉じる。
 *
 * <p>走査は changelog 全域（版付き release と reconcile の双方）で、Liquibase が制約名を宣言する 2 つのキー {@code
 * constraintName} / {@code uniqueConstraintName} の値を集める。「enum の名前が DDL に実在すること」だけを見る片方向の検査で、DDL
 * 側に写像先の無い制約が あることは正常（写像を持たない違反は実装欠陥として大きく失敗させる側）。
 */
class DbConstraintLiteralTests {

  private static final Path CHANGELOG_ROOT = Paths.get("src/main/resources/db/changelog");

  /** Liquibase が制約名を宣言するキーとその値。行末までを名前とし、YAML のクォートは現状使われていないため素の値で取る。 */
  private static final Pattern DECLARED_CONSTRAINT =
      Pattern.compile("(?:constraintName|uniqueConstraintName):\\s*(\\S+)");

  @Test
  @DisplayName("DbConstraint の全成員の制約名が changelog の DDL に実在すること")
  void allDbConstraintNamesExistInChangelog() throws Exception {
    List<Path> changelogFiles;
    try (Stream<Path> paths = Files.walk(CHANGELOG_ROOT)) {
      changelogFiles =
          paths
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".yaml"))
              .toList();
    }

    Set<String> declared = new HashSet<>();
    for (Path file : changelogFiles) {
      Matcher matcher = DECLARED_CONSTRAINT.matcher(Files.readString(file));
      while (matcher.find()) {
        declared.add(matcher.group(1));
      }
    }

    // 暗黙の no-op 防止: 走査が実際に DDL を捉えていることを担保する。
    assertThat(changelogFiles).as("changelog 配下の .yaml").isNotEmpty();
    assertThat(declared).as("DDL が宣言する制約名").isNotEmpty();

    List<String> missing =
        Arrays.stream(DbConstraint.values())
            .map(DbConstraint::sqlName)
            .filter(name -> !declared.contains(name))
            .toList();

    assertThat(missing).as("changelog の DDL に実在しない DbConstraint の制約名").isEmpty();
  }
}
