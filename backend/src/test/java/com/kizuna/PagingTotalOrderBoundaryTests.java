package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.application.MemberDirectoryService;
import com.kizuna.order.application.MonthlyPdfSnapshotService;
import com.kizuna.shared.persistence.Paging;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
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

  /** 照会文の ORDER BY が一意キーで終わり、Pageable は位置だけを運ぶ呼出元（クラス → メソッド）。 */
  private static final Map<String, Set<String>> QUERY_ORDERED =
      Map.of(
          // 一括走査の窓。照会文が o.businessDate desc, o.id desc で並べる
          MonthlyPdfSnapshotService.class.getName(), Set.of("store", "self", "collect"),
          // 並びの列が利用者行に跨るため、照会文が m.createdAt desc, m.id desc で並べる
          MemberDirectoryService.class.getName(), Set.of("search"));

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
    for (var origin : classes) {
      if (origin.isEquivalentTo(Paging.class)) {
        continue;
      }
      var allowed = QUERY_ORDERED.getOrDefault(origin.getName(), Set.of());
      for (var access : origin.getCodeUnitAccessesFromSelf()) {
        var target = access.getTarget();
        if (factories.getOrDefault(target.getOwner().getName(), Set.of()).contains(target.getName())
            && !allowed.contains(access.getOrigin().getName())) {
          offenders.add(access.getDescription());
        }
      }
    }
    assertThat(offenders).as("Paging を迂回して Pageable を組み立てる呼出元").isEmpty();
  }
}
