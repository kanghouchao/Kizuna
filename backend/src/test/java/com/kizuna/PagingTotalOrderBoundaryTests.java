package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.persistence.Paging;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AbstractPageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * offset ページングの Pageable を組み立てる口を {@link Paging} に一つにする。並びが一意キーで終わるかは呼出元ごとに判定できないため、
 * 組み立てそのものを静的に禁じる。
 */
class PagingTotalOrderBoundaryTests {

  /** 照会文の ORDER BY が一意キーで終わり、Pageable は位置だけを運ぶ呼出元。同名の多重定義を足してもすり抜けないよう、引数型までの署名で持つ。 */
  private static final Set<String> QUERY_ORDERED =
      Set.of(
          // 一括走査の窓。照会文が o.businessDate desc, o.id desc で並べる
          "com.kizuna.order.application.MonthlyPdfSnapshotService.store(java.lang.Long,"
              + " java.lang.Long, java.lang.String, com.kizuna.order.application.MonthlyPdfBudget)",
          "com.kizuna.order.application.MonthlyPdfSnapshotService.self(java.lang.String,"
              + " java.lang.Long, java.lang.String, com.kizuna.order.application.MonthlyPdfBudget)",
          "com.kizuna.order.application.MonthlyPdfSnapshotService.collect(java.lang.Long,"
              + " java.lang.Long, java.lang.String, java.lang.String, java.lang.String, long, long,"
              + " com.kizuna.order.application.MonthlyPdfBudget)",
          // 並びの列が利用者行に跨るため、照会文が m.createdAt desc, m.id desc で並べる
          "com.kizuna.member.application.MemberDirectoryService.search("
              + "com.kizuna.member.application.MemberStatusFilter, java.lang.String,"
              + " org.springframework.data.domain.Pageable)");

  @Test
  void pageablesAreBuiltOnlyThroughPaging() {
    var factories =
        Map.of(
            PageRequest.class.getName(), Set.of("of", "ofSize", "withSort"),
            AbstractPageRequest.class.getName(), Set.of("withSort"),
            Pageable.class.getName(), Set.of("ofSize", "withSort"));
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.kizuna");
    var offenders = new ArrayList<String>();
    var allowedUsed = new HashSet<String>();
    for (var origin : classes) {
      if (origin.isEquivalentTo(Paging.class)) {
        continue;
      }
      for (var access : origin.getCodeUnitAccessesFromSelf()) {
        var target = access.getTarget();
        if (!factories
            .getOrDefault(target.getOwner().getName(), Set.of())
            .contains(target.getName())) {
          continue;
        }
        var caller = access.getOrigin().getFullName();
        if (QUERY_ORDERED.contains(caller)) {
          allowedUsed.add(caller);
        } else {
          offenders.add(access.getDescription());
        }
      }
    }
    assertThat(offenders).as("Paging を迂回して Pageable を組み立てる呼出元").isEmpty();
    // 署名の変わった許可が黙って空振りしないよう、許可はすべて実在の呼出元に当たること
    assertThat(allowedUsed).as("呼出元に当たった許可").isEqualTo(QUERY_ORDERED);
  }
}
