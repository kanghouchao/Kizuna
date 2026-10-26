package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/** store_id 列を持つ @Entity は、継承経路に関わらず StoreScopedEntity の共通土台に載っていなければならない。 */
class StoreIsolationTests {

  @Test
  @DisplayName("store_id 列を持つ全 @Entity が StoreScopedEntity を継承していること（豁免なし）")
  void allStoreScopedEntitiesExtendBaseClass() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

    List<String> offenders = new ArrayList<>();
    List<String> scanned = new ArrayList<>();
    for (var candidate : scanner.findCandidateComponents("com.kizuna")) {
      Class<?> entity = Class.forName(candidate.getBeanClassName());
      if (!hasStoreIdColumn(entity)) {
        continue;
      }
      scanned.add(entity.getSimpleName());
      // 基類継承を強制することで、store_id 未設定の永続化を拒む StoreScopeStampListener が全店舗級エンティティに効く。
      if (!StoreScopedEntity.class.isAssignableFrom(entity)) {
        offenders.add(entity.getName());
      }
    }

    assertThat(scanned).isNotEmpty();
    assertThat(offenders)
        .as("store_id 列を持つが StoreScopedEntity を継承していない @Entity（store_id 未設定の検証から外れる）")
        .isEmpty();
  }

  /** store_id 列は StoreScopedEntity 経由（継承フィールド）でも、エンティティ自身の宣言でも良い。 */
  private static boolean hasStoreIdColumn(Class<?> type) {
    for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        // @ElementCollection の @Column は集合テーブルの列（User.storeIds 等の授権店舗集合）であり、
        // 本体テーブルの行識別列ではないため店舗級エンティティの判定から除外する。
        if (field.isAnnotationPresent(ElementCollection.class)) {
          continue;
        }
        Column column = field.getAnnotation(Column.class);
        if (column != null && "store_id".equals(column.name())) {
          return true;
        }
      }
    }
    return false;
  }
}
