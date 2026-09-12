package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.user.application.CredentialOperations;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserCredentialsChanged;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CredentialOperationsBoundaryTests {
  @Test
  void credentialMutationsAndEventConstructionCannotBypassOperations() {
    var protectedOperations =
        Map.of(
            PlatformUser.class.getName(), Set.of("stop", "changePassword", "invalidateSessions"),
            PlatformUserCredentialsChanged.class.getName(), Set.of("<init>"));
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.kizuna");
    var offenders = new ArrayList<String>();
    for (var origin : classes) {
      if (origin.isEquivalentTo(CredentialOperations.class)) {
        continue;
      }
      for (var access : origin.getCodeUnitAccessesFromSelf()) {
        var target = access.getTarget();
        if (protectedOperations
            .getOrDefault(target.getOwner().getName(), Set.of())
            .contains(target.getName())) {
          offenders.add(access.getDescription());
        }
      }
    }
    assertThat(offenders).as("資格情報の統一操作を迂回する呼出元").isEmpty();
  }
}
