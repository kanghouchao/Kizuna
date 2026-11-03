package com.kizuna;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** application は api.dto（wire の形）に依存しない。依存は api → application → domain の内向きで、wire との変換は api に置く。 */
class ApplicationApiDtoBoundaryTests {
  @Test
  void applicationDoesNotDependOnApiDtos() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.kizuna");
    noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..api.dto..")
        .check(classes);
  }
}
