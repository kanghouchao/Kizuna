package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * master changelog の include 順のうち、機構が依存している 1 点を固定する。
 *
 * <p>{@code reconcile/} は版に属さない恒久的な写像取り直しで、{@code runAlways} なので毎回実行される。版付き migration より先に走ると、たとえば
 * ロールの改名では「改名前の行」を見て齟齬と判定し、その回のトランザクションを巻き戻す — 後続にあるはずの改名 migration は永久に実行されない。よって全 release
 * の後に置く必要があり、新しい release の include を末尾へ足すと静かにこの前提が壊れる。
 */
class ChangelogOrderTests {

  private static final Path MASTER_CHANGELOG =
      Paths.get("src/main/resources/db/changelog/db.changelog-master.yaml");

  private static final Pattern INCLUDED_FILE = Pattern.compile("file:\\s*(\\S+)");

  @Test
  @DisplayName("reconcile/ の include が master changelog の最後に置かれていること")
  void reconcileIsIncludedLast() throws Exception {
    List<String> included = new ArrayList<>();
    Matcher matcher = INCLUDED_FILE.matcher(Files.readString(MASTER_CHANGELOG));
    while (matcher.find()) {
      included.add(matcher.group(1));
    }

    // 暗黙の no-op 防止: release の include を実際に読めていること。
    assertThat(included)
        .as("master changelog の include")
        .anyMatch(file -> file.contains("releases/"));
    assertThat(included).last().as("最後の include").isEqualTo("reconcile/permission-catalogue.yaml");
  }
}
