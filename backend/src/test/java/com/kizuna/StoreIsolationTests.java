package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * 店舗行レベル分離の不変量を機械検証する（#208, PR-B）。
 *
 * <p>store_id 列を持つ @Entity は、継承経路（StoreScopedEntity）に関わらず、サービス層の {@code @StoreScoped}（{@code
 * StoreFilterEnable}）が有効化する Hibernate フィルタ {@code storeFilter} の対象でなければならない。 {@code @Filter}
 * が無いエンティティはフィルタが有効でも全店舗の行を返してしまう（#216 の「@Filter を忘れる」事故と同型）。
 */
class StoreIsolationTests {

  private static final String EXPECTED_CONDITION = "store_id = :storeId";

  @Test
  @DisplayName("store_id 列を持つ全 @Entity が storeFilter の @Filter を宣言していること")
  void allStoreScopedEntitiesDeclareStoreFilter() throws Exception {
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
      // @Filter は Hibernate 6 で repeatable のため、storeFilter を宣言していれば
      // 他フィルタ（storeSetFilter 等）が並置されていても違反としない。
      boolean declaresStoreFilter = false;
      for (Filter filter : entity.getAnnotationsByType(Filter.class)) {
        if ("storeFilter".equals(filter.name()) && EXPECTED_CONDITION.equals(filter.condition())) {
          declaresStoreFilter = true;
          break;
        }
      }
      if (!declaresStoreFilter) {
        offenders.add(entity.getName());
      }
    }

    assertThat(scanned).isNotEmpty();
    assertThat(offenders)
        .as(
            "@Filter(name=\"storeFilter\", condition=\"%s\") が無い store_id 列保持エンティティ",
            EXPECTED_CONDITION)
        .isEmpty();
  }

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
      // 基類継承を強制することで、@PrePersist の store_id 採番（StoreScopeStampListener）と
      // storeFilter/@FilterDef の共通土台が全 store-scoped 集約へ機構的に効く。豁免リストは持たない。
      if (!StoreScopedEntity.class.isAssignableFrom(entity)) {
        offenders.add(entity.getName());
      }
    }

    assertThat(scanned).isNotEmpty();
    assertThat(offenders)
        .as("store_id 列を持つが StoreScopedEntity を継承していない @Entity（採番・行レベル分離の共通土台から外れる）")
        .isEmpty();
  }

  @Test
  @DisplayName("storeFilter は主キー直接ロード（EntityManager#find 経由の findById 等）にも適用されること")
  void storeFilterAppliesToLoadByKey() {
    // @FilterDef は Hibernate 6 で repeatable のため、storeFilter の定義を名前で取り出す
    // （storeSetFilter 等が並置されていても getAnnotation は container を返し null になるため）。
    FilterDef storeFilterDef = null;
    for (FilterDef filterDef : StoreScopedEntity.class.getAnnotationsByType(FilterDef.class)) {
      if ("storeFilter".equals(filterDef.name())) {
        storeFilterDef = filterDef;
        break;
      }
    }

    assertThat(storeFilterDef)
        .as("StoreScopedEntity が storeFilter の @FilterDef を宣言していること")
        .isNotNull();
    assertThat(storeFilterDef.applyToLoadByKey())
        .as(
            "applyToLoadByKey=false だと Session#find（Spring Data JPA の findById 実装経路）に"
                + " filter が効かず、他店舗の ID を直接指定した読み取りが素通りする")
        .isTrue();
  }

  /** store_id 列は StoreScopedEntity 経由（継承フィールド）でも、エンティティ自身の宣言でも良い。 */
  private static boolean hasStoreIdColumn(Class<?> type) {
    for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        // @ElementCollection の @Column は集合テーブルの列（PlatformUser.storeIds 等の授権店舗集合）であり、
        // 本体テーブルの行識別列ではないため店舗フィルタの対象判定から除外する。
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
