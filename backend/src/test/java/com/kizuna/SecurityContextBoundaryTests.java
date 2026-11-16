package com.kizuna;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 認証文脈を読むのは入口（api）と web の差し込み（infrastructure）、認証そのものを扱う auth と shared だけにする。 それ以外は実行者や権限を引数で受ける —
 * 文脈を読む service は、要求の外（定期処理・イベント）から呼ぶと実行者を失う。
 */
class SecurityContextBoundaryTests {
  @Test
  void onlyEntryPointsReadTheSecurityContext() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.kizuna");
    noClasses()
        .that()
        .resideOutsideOfPackages(
            "..api..", "..infrastructure..", "com.kizuna.auth..", "com.kizuna.shared..")
        .should()
        .dependOnClassesThat()
        .areAssignableTo(SecurityContextHolder.class)
        .check(classes);
  }
}
