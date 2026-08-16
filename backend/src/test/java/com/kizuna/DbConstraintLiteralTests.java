package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.exception.DbConstraint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
 * constraintName} / {@code uniqueConstraintName} の値と、素の SQL で作る一意索引の名前を集める。索引は制約ではないが、PostgreSQL
 * は一意索引の違反も索引名を制約名として報告するため、写像先として同格に扱える（Liquibase に部分一意索引の構文が無く、{@code where} 付きの一意は素の SQL
 * でしか書けない）。
 *
 * <p>「enum の名前が DDL に実在すること」だけを見る片方向の検査で、DDL 側に写像先の無い制約が あることは正常（写像を持たない違反は実装欠陥として大きく失敗させる側）。
 */
class DbConstraintLiteralTests {

  private static final Path CHANGELOG_ROOT = Paths.get("src/main/resources/db/changelog");

  /** Liquibase が制約名を宣言するキーとその値。行末までを名前とし、YAML のクォートは現状使われていないため素の値で取る。 */
  private static final Pattern DECLARED_CONSTRAINT =
      Pattern.compile("(?:constraintName|uniqueConstraintName):\\s*(\\S+)");

  /**
   * 素の SQL で作る一意索引の名前。
   *
   * <p>{@code IF NOT EXISTS} 付きの形は現状存在せず、書かれれば {@code IF} を名前として拾い、当該 enum が実在しないものとして赤くなる —
   * 見落としではなく大声の失敗になる側に倒している。
   */
  private static final Pattern DECLARED_UNIQUE_INDEX =
      Pattern.compile("CREATE UNIQUE INDEX\\s+(\\S+)");

  /**
   * Liquibase の外部キー宣言 1 件分。ダッシュより深く字下げされた行が本体で、その中の {@code constraintName} と {@code onDelete} を読む。
   */
  private static final Pattern FOREIGN_KEY_BLOCK =
      Pattern.compile(
          "(?m)^(?<indent>[ ]*)- addForeignKeyConstraint:\\n"
              + "(?<body>(?:\\k<indent>[ ]+.*\\n|[ ]*\\n)*)");

  private static final Pattern BLOCK_CONSTRAINT_NAME = Pattern.compile("constraintName:\\s*(\\S+)");

  private static final Pattern BLOCK_ON_DELETE = Pattern.compile("onDelete:\\s*(.+)");

  @Test
  @DisplayName("DbConstraint の全成員の制約名が changelog の DDL に実在すること")
  void allDbConstraintNamesExistInChangelog() throws Exception {
    List<Path> changelogFiles = changelogFiles();

    Set<String> declared = new HashSet<>();
    for (Path file : changelogFiles) {
      String content = Files.readString(file);
      collectNames(DECLARED_CONSTRAINT.matcher(content), declared);
      collectNames(DECLARED_UNIQUE_INDEX.matcher(content), declared);
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

  /**
   * 写像先を持つ外部キーが RESTRICT で宣言されていないことを見る。
   *
   * <p>PostgreSQL は RESTRICT 違反を SQLSTATE 23001 で報告し、その文言から Hibernate は制約名を取り出さない（{@code
   * constraint=null}）。字面が DDL に実在していても写像は決して命中せず、削除の拒否が 500 に化ける — 前のテストと同じ欠陥類の、字面では見えないほうの穴である。
   */
  @Test
  @DisplayName("DbConstraint に載る外部キーが RESTRICT で宣言されていないこと")
  void mappedForeignKeysAreNotDeclaredWithRestrict() throws Exception {
    Set<String> mapped =
        Arrays.stream(DbConstraint.values()).map(DbConstraint::sqlName).collect(Collectors.toSet());

    List<String> restricted = new ArrayList<>();
    int blocks = 0;
    for (Path file : changelogFiles()) {
      Matcher block = FOREIGN_KEY_BLOCK.matcher(Files.readString(file));
      while (block.find()) {
        blocks++;
        String body = block.group("body");
        Matcher name = BLOCK_CONSTRAINT_NAME.matcher(body);
        Matcher onDelete = BLOCK_ON_DELETE.matcher(body);
        if (name.find() && onDelete.find() && mapped.contains(name.group(1))) {
          if ("RESTRICT".equalsIgnoreCase(onDelete.group(1).trim())) {
            restricted.add(name.group(1));
          }
        }
      }
    }

    // 暗黙の no-op 防止: 外部キー宣言そのものを捉えられていることを担保する。
    assertThat(blocks).as("changelog が宣言する外部キーの件数").isPositive();

    assertThat(restricted).as("RESTRICT で宣言されている DbConstraint の外部キー").isEmpty();
  }

  private static List<Path> changelogFiles() throws Exception {
    try (Stream<Path> paths = Files.walk(CHANGELOG_ROOT)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".yaml"))
          .toList();
    }
  }

  private static void collectNames(Matcher matcher, Set<String> into) {
    while (matcher.find()) {
      into.add(matcher.group(1));
    }
  }
}
