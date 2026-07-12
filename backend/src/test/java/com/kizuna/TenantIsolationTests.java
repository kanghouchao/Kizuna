package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
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
 * テナント行レベル分離の不変量を機械検証する（#208, PR-B）。
 *
 * <p>tenant_id 列を持つ @Entity は、継承経路（TenantScopedEntity）に関わらず、サービス層の {@code @TenantScoped}（{@code
 * TenantFilterEnable}）が有効化する Hibernate フィルタ {@code tenantFilter} の対象でなければならない。 {@code @Filter}
 * が無いエンティティはフィルタが有効でも全テナントの行を返してしまう（#216 の「@Filter を忘れる」事故と同型）。
 */
class TenantIsolationTests {

  private static final String EXPECTED_CONDITION = "tenant_id = :tenantId";

  @Test
  @DisplayName("tenant_id 列を持つ全 @Entity が tenantFilter の @Filter を宣言していること")
  void allTenantScopedEntitiesDeclareTenantFilter() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

    List<String> offenders = new ArrayList<>();
    List<String> scanned = new ArrayList<>();
    for (var candidate : scanner.findCandidateComponents("com.kizuna")) {
      Class<?> entity = Class.forName(candidate.getBeanClassName());
      if (!hasTenantIdColumn(entity)) {
        continue;
      }
      scanned.add(entity.getSimpleName());
      // @Filter は Hibernate 6 で repeatable のため、tenantFilter を宣言していれば
      // 他フィルタ（storeSetFilter 等）が並置されていても違反としない。
      boolean declaresTenantFilter = false;
      for (Filter filter : entity.getAnnotationsByType(Filter.class)) {
        if ("tenantFilter".equals(filter.name()) && EXPECTED_CONDITION.equals(filter.condition())) {
          declaresTenantFilter = true;
          break;
        }
      }
      if (!declaresTenantFilter) {
        offenders.add(entity.getName());
      }
    }

    assertThat(scanned).isNotEmpty();
    assertThat(offenders)
        .as(
            "@Filter(name=\"tenantFilter\", condition=\"%s\") が無い tenant_id 列保持エンティティ",
            EXPECTED_CONDITION)
        .isEmpty();
  }

  @Test
  @DisplayName("tenantFilter は主キー直接ロード（EntityManager#find 経由の findById 等）にも適用されること")
  void tenantFilterAppliesToLoadByKey() {
    // @FilterDef は Hibernate 6 で repeatable のため、tenantFilter の定義を名前で取り出す
    // （storeSetFilter 等が並置されていても getAnnotation は container を返し null になるため）。
    FilterDef tenantFilterDef = null;
    for (FilterDef filterDef : TenantScopedEntity.class.getAnnotationsByType(FilterDef.class)) {
      if ("tenantFilter".equals(filterDef.name())) {
        tenantFilterDef = filterDef;
        break;
      }
    }

    assertThat(tenantFilterDef)
        .as("TenantScopedEntity が tenantFilter の @FilterDef を宣言していること")
        .isNotNull();
    assertThat(tenantFilterDef.applyToLoadByKey())
        .as(
            "applyToLoadByKey=false だと Session#find（Spring Data JPA の findById 実装経路）に"
                + " filter が効かず、他テナントの ID を直接指定した読み取りが素通りする")
        .isTrue();
  }

  /** tenant_id 列は TenantScopedEntity 経由（継承フィールド）でも、エンティティ自身の宣言でも良い。 */
  private static boolean hasTenantIdColumn(Class<?> type) {
    for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && "tenant_id".equals(column.name())) {
          return true;
        }
      }
    }
    return false;
  }
}
