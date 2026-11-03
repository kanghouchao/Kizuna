package com.kizuna;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

/**
 * application は api.dto（wire の形）に依存しない。依存は api → application → domain の内向きで、wire との変換は api に置く。
 *
 * <p>既存の違反は src/test/resources/archunit-store に凍結してあり、新しい違反は赤、解消した違反は試験の実行で記録から消える。記録は
 * archunit.properties の allowStoreCreation を true にして一度だけ生成し、false に戻してコミットする。
 */
class ApplicationApiDtoBoundaryTests {
  @Test
  void applicationDoesNotDependOnApiDtos() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.kizuna");
    FreezingArchRule.freeze(
            noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..api.dto.."))
        .check(classes);
  }
}
