package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class OrderBirthBoundaryTests {
  @Test
  void orderIsBuiltOnlyInsideTheDomainPackage() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.kizuna");
    var offenders = new ArrayList<String>();
    for (var origin : classes) {
      if (origin.getPackageName().equals(Order.class.getPackageName())) {
        continue;
      }
      for (var access : origin.getCodeUnitAccessesFromSelf()) {
        var target = access.getTarget();
        if (target.getOwner().getName().equals(Order.class.getName())
                && target.getName().equals("builder")
            || target.getOwner().getName().equals(Order.class.getName() + "$OrderBuilder")) {
          offenders.add(access.getDescription());
        }
      }
    }
    assertThat(offenders).as("Order の出生は静的ファクトリだけが担う").isEmpty();
  }
}
