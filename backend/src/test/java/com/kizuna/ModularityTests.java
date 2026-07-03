package com.kizuna;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

  // 旧フラット構造は循環依存があるため検証対象外（docs/ddd-fsd-refactor-plan.md の PR2〜PR6 で
  // モジュール移行が進むたびにここから削除し、PR9 でこの除外リスト自体を消す）
  private static final DescribedPredicate<JavaClass> LEGACY_PACKAGES =
      JavaClass.Predicates.resideInAnyPackage(
          "com.kizuna.config..",
          "com.kizuna.controller..",
          "com.kizuna.exception..",
          "com.kizuna.mapper..",
          "com.kizuna.model..",
          "com.kizuna.repository..",
          "com.kizuna.service..",
          "com.kizuna.utils..");

  @Test
  void verifiesModularity() {
    ApplicationModules.of(Application.class, LEGACY_PACKAGES).verify();
  }
}
