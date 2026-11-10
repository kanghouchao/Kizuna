package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Entity;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.mapping.PersistentClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.hibernate.SpringImplicitNamingStrategy;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/** store_id 列を持つ @Entity は、継承経路に関わらず StoreScopedEntity の共通土台に載っていなければならない。 */
class StoreIsolationTests {

  @Test
  @DisplayName("store_id 列を持つ全 @Entity が StoreScopedEntity を継承していること（豁免なし）")
  void everyStoreScopedEntityExtendsBaseClass() {
    try (StandardServiceRegistry registry = serviceRegistryWithoutDatabase()) {
      Metadata metadata = bootModel(registry);

      List<String> offenders = new ArrayList<>();
      List<String> scanned = new ArrayList<>();
      List<String> onBaseClass = new ArrayList<>();
      for (PersistentClass binding : metadata.getEntityBindings()) {
        Class<?> entity = binding.getMappedClass();
        if (StoreScopedEntity.class.isAssignableFrom(entity)) {
          onBaseClass.add(entity.getSimpleName());
        }
        if (!hasStoreIdColumn(binding)) {
          continue;
        }
        scanned.add(entity.getSimpleName());
        // 基類継承を強制することで、store_id 未設定の永続化を拒む StoreScopeStampListener が全店舗級エンティティに効く。
        if (!StoreScopedEntity.class.isAssignableFrom(entity)) {
          offenders.add(entity.getName());
        }
      }

      // 判定が列を見失えば違反ゼロで黙って通るため、基類を継承する実体を全て拾えていることを先に確かめる。
      assertThat(scanned).isNotEmpty().containsAll(onBaseClass);
      assertThat(offenders)
          .as("store_id 列を持つが StoreScopedEntity を継承していない @Entity（store_id 未設定の検証から外れる）")
          .isEmpty();
    }
  }

  /**
   * 実体の本体テーブルに store_id 列があるかを、Hibernate が解決した物理列名で判定する。@ElementCollection の store_id（User.storeIds
   * 等の授権店舗集合）は集合テーブルの列なので本体テーブルには現れず、自然に除外される。
   */
  private static boolean hasStoreIdColumn(PersistentClass binding) {
    return binding.getTable().getColumns().stream()
        .anyMatch(column -> "store_id".equalsIgnoreCase(column.getName()));
  }

  /**
   * 命名戦略は実行時（Spring Boot 既定）と同じものを明示的に与える。name 無しの @Column や @JoinColumn も実スキーマと
   * 同じ列名に解決されるので、フィールド名や注釈の書き方を推測する判定より取りこぼしが無い。
   */
  private static Metadata bootModel(StandardServiceRegistry registry) {
    MetadataSources sources = new MetadataSources(registry);
    entities().forEach(sources::addAnnotatedClass);
    return sources
        .getMetadataBuilder()
        .applyImplicitNamingStrategy(new SpringImplicitNamingStrategy())
        .applyPhysicalNamingStrategy(new PhysicalNamingStrategySnakeCaseImpl())
        .build();
  }

  /** 写像の束縛だけが要るので、接続を持たず方言を固定した registry で足りる。 */
  private static StandardServiceRegistry serviceRegistryWithoutDatabase() {
    return new StandardServiceRegistryBuilder()
        .applySetting(JdbcSettings.DIALECT, PostgreSQLDialect.class.getName())
        .applySetting(JdbcSettings.ALLOW_METADATA_ON_BOOT, false)
        .build();
  }

  private static List<Class<?>> entities() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    List<Class<?>> classes = new ArrayList<>();
    for (var candidate : scanner.findCandidateComponents("com.kizuna")) {
      try {
        classes.add(Class.forName(candidate.getBeanClassName()));
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException(e);
      }
    }
    return classes;
  }
}
