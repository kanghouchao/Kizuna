package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.user.application.CredentialOperations;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

  static final ApplicationModules modules = ApplicationModules.of(Application.class);

  @Test
  void verifiesModularity() {
    modules.verify();
  }

  @Test
  void exposesOnlyCredentialOperationsThroughNamedInterface() {
    var boundary =
        modules
            .getModuleByName("user")
            .orElseThrow()
            .getNamedInterfaces()
            .getByName("credential-operations")
            .orElseThrow();
    assertThat(boundary.asJavaClasses())
        .extracting(type -> type.getName())
        .containsExactly(CredentialOperations.class.getName());
  }

  // 広告費・受注・応募者が媒体を参照する向きだけを許し、媒体からは業務モジュールを呼ばない。
  @Test
  void mediumDependsOnNoBusinessModuleAndExposesOnlyItsLookup() {
    var medium = modules.getModuleByName("medium").orElseThrow();
    assertThat(medium.getDirectDependencies(modules).uniqueModules())
        .extracting(module -> module.getIdentifier().toString())
        .containsExactlyInAnyOrder("shared", "user");
    assertThat(medium.getNamedInterfaces().getByName("lookup").orElseThrow().asJavaClasses())
        .extracting(type -> type.getSimpleName())
        .containsExactlyInAnyOrder("MediumLookup", "MediumReference");
  }

  @Test
  void writesModuleDocumentation() {
    new Documenter(modules).writeDocumentation();
  }
}
