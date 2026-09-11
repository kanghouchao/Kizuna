package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.application.MemberRankService;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.point.application.BenefitGrantService;
import com.kizuna.point.application.PointLedgerService;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttributionMaterializationTests {
  private static final String MATERIALIZER = "com.kizuna.order.application.AttributionMaterializer";
  private static final Map<String, Set<String>> MATERIALIZATION_OPERATIONS =
      Map.of(
          OrderAttribution.class.getName(), Set.of("<init>", "onCompletion", "onReceiptClaim"),
          MemberRankService.class.getName(), Set.of("lockForPromotion"),
          PointLedgerService.class.getName(), Set.of("grantForOrder", "grantPlannedForOrder"),
          BenefitGrantService.class.getName(), Set.of("grantVisitBenefits"));

  @Test
  @DisplayName("帰属生成・会員ロック・受注付与・特典の外部呼出は物化の入口に集約されること")
  void productionCodeCannotBypassMaterialization() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.kizuna");
    var offenders = new ArrayList<String>();
    for (JavaClass origin : classes) {
      if (origin.getName().equals(MATERIALIZER)) {
        continue;
      }
      for (var access : origin.getCodeUnitAccessesFromSelf()) {
        var target = access.getTarget();
        // 生成口自身の構築と台帳サービス内の委譲は、外部からの編排ではない。
        if (target.getOwner().equals(origin)) {
          continue;
        }
        if (MATERIALIZATION_OPERATIONS
            .getOrDefault(target.getOwner().getName(), Set.of())
            .contains(target.getName())) {
          offenders.add(access.getDescription());
        }
      }
    }
    assertThat(offenders).as("帰属の物化を迂回する呼出元").isEmpty();
  }
}
