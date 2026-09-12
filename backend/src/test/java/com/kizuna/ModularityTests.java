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

  @Test
  void writesModuleDocumentation() {
    new Documenter(modules).writeDocumentation();
  }
}
