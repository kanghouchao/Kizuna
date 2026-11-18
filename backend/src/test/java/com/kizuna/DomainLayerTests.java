package com.kizuna;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.kizuna.menu.domain.Menu;
import com.kizuna.service.domain.ServiceRevisionRepository;
import com.kizuna.settings.domain.SystemConfig;
import com.kizuna.store.domain.Store;
import com.kizuna.storeprofile.domain.StoreProfile;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

/** domain は内側の層で、外の層を知らず、集約の項目は振る舞いのメソッドでだけ変わる。 */
class DomainLayerTests {

  static final JavaClasses classes =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.kizuna");

  // ServiceRevisionRepository は application の射影を戻り値にしており、この向きの唯一の例外。
  @Test
  void domainDoesNotDependOnOuterLayers() {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .and()
        .doNotBelongToAnyOf(ServiceRevisionRepository.class)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.kizuna..application..", "com.kizuna..api..", "com.kizuna..infrastructure..")
        .check(classes);
  }

  // Lombok の @Setter はバイトコードに残らないので、宣言された public な setXxx を見る。
  // 除いた 4 つは公開 setter を持ったままの集約で、ここに足さない。
  @Test
  void aggregatesDeclareNoPublicSetters() {
    noMethods()
        .that()
        .areDeclaredInClassesThat(
            resideInAPackage("..domain..")
                .and(annotatedWith(Entity.class))
                .and(
                    not(
                        belongToAnyOf(
                            Store.class, Menu.class, SystemConfig.class, StoreProfile.class))))
        .and()
        .arePublic()
        .should()
        .haveNameMatching("set[A-Z].*")
        .check(classes);
  }
}
