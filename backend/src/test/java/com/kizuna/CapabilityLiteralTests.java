package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.user.domain.Authorities;
import com.kizuna.user.domain.Capability;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 権限 authority 字面（{@code PERM_…}）と {@link Capability} enum の整合を機械検証する（#430）。
 *
 * <p>{@code @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")} の字面は多数の Controller に手書きされており、{@code
 * Capability} enum とのコンパイル期関連が一切ない。typo は「到達不能な権限（誰も持たない authority = 全員 403）」を静默に生み、機能 IT
 * が偶然踏まない限り検出されない。{@link StoreIsolationTests}（#216 由来）と同型の fitness test でこの欠陥類を閉じる。
 *
 * <p>検査は字面 → enum の写像のみで、SpEL の書き方には一切干渉しない（{@code hasRole} 等の形状は授権設計の判断であり、機械検査の対象外）。
 *
 * <p>対象は SpEL の文字列リテラル（単引用符）である。Spring が比較するのは引用符の内側**全体**であるため、部分一致ではなくリテラル全体を照合する （{@code
 * 'PERM_ORDER_MANAGE '} のように前後にごみが付いた字面も、Capability には写像できない到達不能な権限として検出する）。 Java の二重引用符で書かれた
 * authority（現状 main に存在しない）は対象外で、導入時はこのテストの拡張を要する。
 */
class CapabilityLiteralTests {

  /**
   * 権限 authority を含む SpEL 文字列リテラル。捕獲群はリテラル全体で、接頭辞の知識は {@link Authorities} の唯一の継ぎ目から取る。 改行を跨ぐ
   * 組み合わせは別々のリテラルの誤結合であるため除外する。
   */
  private static final Pattern PERMISSION_LITERAL =
      Pattern.compile("'([^'\\n]*" + Pattern.quote(Authorities.permission("")) + "[^'\\n]*)'");

  @Test
  @DisplayName("src/main/java 中の全 PERM_ 字面が Capability 成員へ写像できること")
  void allPermissionLiteralsMapToCapability() throws Exception {
    Set<String> known =
        Arrays.stream(Capability.values()).map(Capability::authority).collect(Collectors.toSet());

    Path root = Paths.get("src/main/java");
    List<Path> javaFiles;
    try (Stream<Path> paths = Files.walk(root)) {
      javaFiles =
          paths
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".java"))
              .toList();
    }

    List<String> offenders = new ArrayList<>();
    int literals = 0;
    for (Path file : javaFiles) {
      Matcher matcher = PERMISSION_LITERAL.matcher(Files.readString(file));
      while (matcher.find()) {
        literals++;
        if (!known.contains(matcher.group(1))) {
          offenders.add(root.relativize(file) + ": '" + matcher.group(1) + "'");
        }
      }
    }

    // 暗黙の no-op 防止: 走査が実際に対象を捉えていることを担保する。
    assertThat(javaFiles).as("src/main/java 配下の .java ソース").isNotEmpty();
    assertThat(literals).as("抽出した PERM_ 字面の総数").isGreaterThan(0);

    assertThat(offenders).as("Capability 成員へ写像できない PERM_ 字面").isEmpty();
  }
}
