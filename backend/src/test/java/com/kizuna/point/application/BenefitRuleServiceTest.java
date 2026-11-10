package com.kizuna.point.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kizuna.point.domain.BenefitRule;
import com.kizuna.point.domain.BenefitRuleDefinition;
import com.kizuna.point.domain.BenefitRuleRepeatPolicy;
import com.kizuna.point.domain.BenefitRuleRepository;
import com.kizuna.point.domain.BenefitRuleStoreScopeType;
import com.kizuna.point.domain.BenefitRuleType;
import com.kizuna.point.domain.InvalidBenefitRuleException;
import com.kizuna.point.domain.StaleBenefitRuleUpdateException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.application.BusinessAudit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BenefitRuleServiceTest {

  @Mock private BenefitRuleRepository benefitRuleRepository;

  @Mock private BusinessAudit businessAudit;

  @InjectMocks private BenefitRuleService benefitRuleService;

  private BenefitRule visitRule;

  @BeforeEach
  void setUp() {
    visitRule =
        BenefitRule.define(
            BenefitRuleType.VISIT,
            BenefitRuleDefinition.builder()
                .name("来店ボーナス")
                .storeScopeType(BenefitRuleStoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of(3L, 5L))
                .repeatPolicy(BenefitRuleRepeatPolicy.EVERY_TIME)
                .points(500)
                .build());
    ReflectionTestUtils.setField(visitRule, "version", 0L);
  }

  @Test
  @DisplayName("一覧は新しい順の問い合わせの規則をそのまま返すこと")
  void listReturnsTheNewestFirstPage() {
    when(benefitRuleRepository.findAllByOrderByCreatedAtDescIdDesc(any()))
        .thenReturn(new PageImpl<>(List.of(visitRule)));

    List<BenefitRule> rows = benefitRuleService.list(PageRequest.of(0, 20)).getContent();

    assertThat(rows).containsExactly(visitRule);
  }

  @Test
  @DisplayName("詳細は編集フォームが要る店舗 ID の列挙を持つ規則を返すこと")
  void detailCarriesStoreIds() {
    when(benefitRuleRepository.findById(1L)).thenReturn(Optional.of(visitRule));

    BenefitRule rule = benefitRuleService.get(1L);

    assertThat(rule.getStoreIds()).containsExactlyInAnyOrder(3L, 5L);
    assertThat(rule.getRepeatPolicy()).isEqualTo(BenefitRuleRepeatPolicy.EVERY_TIME);
  }

  @Test
  @DisplayName("存在しない規則の参照は 404 系の例外になること")
  void missingRuleIsNotFound() {
    when(benefitRuleRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> benefitRuleService.get(99L)).isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("作成は要求の種別で規則を建てること")
  void createDefinesTheRuleWithTheRequestedType() {
    var definition =
        BenefitRuleDefinition.builder()
            .name("紹介キャンペーン")
            .storeScopeType(BenefitRuleStoreScopeType.ALL_STORES)
            .repeatPolicy(BenefitRuleRepeatPolicy.EVERY_TIME)
            .referrerPoints(1000)
            .referredPoints(500)
            .build();
    when(benefitRuleRepository.saveAndFlush(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    BenefitRule rule = benefitRuleService.create(BenefitRuleType.REFERRAL, definition);

    assertThat(rule.getType()).isEqualTo(BenefitRuleType.REFERRAL);
    assertThat(rule.getReferrerPoints()).isEqualTo(1000);
    assertThat(rule.getReferredPoints()).isEqualTo(500);
  }

  @Test
  @DisplayName("更新は種別を動かさずに残りの五要素を置き換えること")
  void updateKeepsTheType() {
    var definition =
        BenefitRuleDefinition.builder()
            .name("来店ボーナス（改）")
            .storeScopeType(BenefitRuleStoreScopeType.ALL_STORES)
            .repeatPolicy(BenefitRuleRepeatPolicy.ONCE_PER_MEMBER)
            .points(300)
            .grantValidityDays(90)
            .build();
    when(benefitRuleRepository.findById(1L)).thenReturn(Optional.of(visitRule));
    when(benefitRuleRepository.saveAndFlush(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    BenefitRule rule = benefitRuleService.update(1L, 0L, definition);

    assertThat(rule.getType()).isEqualTo(BenefitRuleType.VISIT);
    assertThat(rule.getName()).isEqualTo("来店ボーナス（改）");
    assertThat(rule.getStoreIds()).isEmpty();
    assertThat(rule.getGrantValidityDays()).isEqualTo(90);
  }

  @Test
  @DisplayName("陳腐化した編集フォームの提出は 409 で撥ねられること")
  void staleUpdateIsRejected() {
    var definition =
        BenefitRuleDefinition.builder()
            .name("来店ボーナス（改）")
            .storeScopeType(BenefitRuleStoreScopeType.ALL_STORES)
            .repeatPolicy(BenefitRuleRepeatPolicy.EVERY_TIME)
            .points(300)
            .build();
    when(benefitRuleRepository.findById(1L)).thenReturn(Optional.of(visitRule));

    assertThatThrownBy(() -> benefitRuleService.update(1L, 1L, definition))
        .isInstanceOf(StaleBenefitRuleUpdateException.class);
    assertThat(visitRule.getName()).isEqualTo("来店ボーナス");
  }

  @Test
  @DisplayName("停用は集約の標識を倒し、二度目は撥ねられること")
  void deactivationIsOneWay() {
    when(benefitRuleRepository.findById(1L)).thenReturn(Optional.of(visitRule));

    benefitRuleService.deactivate(1L, 0L);

    assertThat(visitRule.getEnabled()).isFalse();
    assertThatThrownBy(() -> benefitRuleService.deactivate(1L, 0L))
        .isInstanceOf(InvalidBenefitRuleException.class);
  }

  @Test
  @DisplayName("停用も確認した版を照合し、ずれていれば規則を消さないこと")
  void deactivationRefusesStaleVersion() {
    when(benefitRuleRepository.findById(1L)).thenReturn(Optional.of(visitRule));

    assertThatThrownBy(() -> benefitRuleService.deactivate(1L, 1L))
        .isInstanceOf(StaleBenefitRuleUpdateException.class);
    assertThat(visitRule.getEnabled()).isTrue();
  }
}
