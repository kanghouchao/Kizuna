package com.kizuna.point.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.point.domain.BenefitRule;
import com.kizuna.point.domain.BenefitRuleDefinition;
import com.kizuna.point.domain.BenefitRuleRepeatPolicy;
import com.kizuna.point.domain.BenefitRuleType;
import com.kizuna.user.domain.StoreScopeType;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BenefitRuleMapperTest {
  private final BenefitRuleMapper mapper = new BenefitRuleMapperImpl();
  private final BenefitRule rule =
      BenefitRule.define(
          BenefitRuleType.VISIT,
          BenefitRuleDefinition.builder()
              .name("来店ボーナス")
              .storeScopeType(StoreScopeType.SPECIFIC_STORES)
              .storeIds(Set.of(3L, 5L))
              .repeatPolicy(BenefitRuleRepeatPolicy.EVERY_TIME)
              .points(500)
              .build());

  @Test
  @DisplayName("一覧の要約は適用店舗を件数へ畳むこと")
  void summaryFoldsStoresIntoACount() {
    var summary = mapper.toSummary(rule);

    assertThat(summary.storeCount()).isEqualTo(2);
    assertThat(summary.type()).isEqualTo("VISIT");
  }

  @Test
  @DisplayName("詳細は編集フォームが要る店舗 ID の列挙を返すこと")
  void responseCarriesStoreIds() {
    var response = mapper.toResponse(rule);

    assertThat(response.storeIds()).containsExactlyInAnyOrder(3L, 5L);
    assertThat(response.repeatPolicy()).isEqualTo("EVERY_TIME");
  }
}
