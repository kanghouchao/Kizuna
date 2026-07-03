package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.persistence.TenantScopedEntity;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * テナント行レベル分離の不変量を機械検証する（#208）。
 *
 * <p>TenantScopedEntity を継承する集約は、サービス層の {@code @TenantScoped}（{@code TenantFilterEnable}）が有効化する
 * Hibernate フィルタ {@code tenantFilter} の対象でなければならない。 {@code @Filter}
 * が無いエンティティはフィルタが有効でも全テナントの行を返してしまう。
 */
class TenantIsolationTests {

  private static final String EXPECTED_CONDITION = "tenant_id = :tenantId";

  @Test
  @DisplayName("TenantScopedEntity の全サブクラスが tenantFilter の @Filter を宣言していること")
  void allTenantScopedEntitiesDeclareTenantFilter() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(TenantScopedEntity.class));

    List<String> offenders = new ArrayList<>();
    List<String> scanned = new ArrayList<>();
    for (var candidate : scanner.findCandidateComponents("com.kizuna")) {
      Class<?> entity = Class.forName(candidate.getBeanClassName());
      if (entity == TenantScopedEntity.class) {
        continue;
      }
      scanned.add(entity.getSimpleName());
      Filter filter = entity.getAnnotation(Filter.class);
      if (filter == null
          || !"tenantFilter".equals(filter.name())
          || !EXPECTED_CONDITION.equals(filter.condition())) {
        offenders.add(entity.getName());
      }
    }

    assertThat(scanned).isNotEmpty();
    assertThat(offenders)
        .as("@Filter(name=\"tenantFilter\", condition=\"%s\") が無いテナントスコープ集約", EXPECTED_CONDITION)
        .isEmpty();
  }
}
