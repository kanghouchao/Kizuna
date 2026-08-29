package com.kizuna.point.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kizuna.point.api.dto.BenefitRuleCreateRequest;
import com.kizuna.point.api.dto.BenefitRuleMapperImpl;
import com.kizuna.point.api.dto.BenefitRuleResponse;
import com.kizuna.point.api.dto.BenefitRuleSummaryResponse;
import com.kizuna.point.api.dto.BenefitRuleUpdateRequest;
import com.kizuna.point.domain.BenefitRule;
import com.kizuna.point.domain.BenefitRuleDefinition;
import com.kizuna.point.domain.BenefitRuleRepeatPolicy;
import com.kizuna.point.domain.BenefitRuleRepository;
import com.kizuna.point.domain.BenefitRuleType;
import com.kizuna.point.domain.InvalidBenefitRuleException;
import com.kizuna.point.domain.StaleBenefitRuleUpdateException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.domain.StoreScopeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BenefitRuleServiceTest {

  @Mock private BenefitRuleRepository benefitRuleRepository;

  @Spy private BenefitRuleMapperImpl benefitRuleMapper;

  @InjectMocks private BenefitRuleService benefitRuleService;

  private BenefitRule visitRule;

  @BeforeEach
  void setUp() {
    visitRule =
        BenefitRule.define(
            BenefitRuleType.VISIT,
            BenefitRuleDefinition.builder()
                .name("来店ボーナス")
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of(3L, 5L))
                .repeatPolicy(BenefitRuleRepeatPolicy.EVERY_TIME)
                .points(500)
                .build());
    ReflectionTestUtils.setField(visitRule, "version", 0L);
  }

  @Test
  @DisplayName("一覧の要約は適用店舗を件数へ畳み、店舗 ID を持たないこと")
  void listFoldsStoresIntoACount() {
    when(benefitRuleRepository.findAllByOrderByCreatedAtDescIdDesc(any()))
        .thenReturn(new PageImpl<>(List.of(visitRule)));

    List<BenefitRuleSummaryResponse> rows =
        benefitRuleService.list(PageRequest.of(0, 20)).getContent();

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().storeCount()).isEqualTo(2);
    assertThat(rows.getFirst().type()).isEqualTo("VISIT");
  }

  @Test
  @DisplayName("詳細は編集フォームが要る店舗 ID の列挙を返すこと")
  void detailCarriesStoreIds() {
    when(benefitRuleRepository.findById(1L)).thenReturn(Optional.of(visitRule));

    BenefitRuleResponse response = benefitRuleService.get(1L);

    assertThat(response.storeIds()).containsExactlyInAnyOrder(3L, 5L);
    assertThat(response.repeatPolicy()).isEqualTo("EVERY_TIME");
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
    BenefitRuleCreateRequest request = new BenefitRuleCreateRequest();
    request.setName("紹介キャンペーン");
    request.setType(BenefitRuleType.REFERRAL);
    request.setStoreScopeType(StoreScopeType.ALL_STORES);
    request.setRepeatPolicy(BenefitRuleRepeatPolicy.EVERY_TIME);
    request.setReferrerPoints(1000);
    request.setReferredPoints(500);
    when(benefitRuleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    BenefitRuleResponse response = benefitRuleService.create(request);

    assertThat(response.type()).isEqualTo("REFERRAL");
    assertThat(response.referrerPoints()).isEqualTo(1000);
    assertThat(response.referredPoints()).isEqualTo(500);
  }

  @Test
  @DisplayName("更新は種別を動かさずに残りの五要素を置き換えること")
  void updateKeepsTheType() {
    BenefitRuleUpdateRequest request = new BenefitRuleUpdateRequest();
    request.setName("来店ボーナス（改）");
    request.setStoreScopeType(StoreScopeType.ALL_STORES);
    request.setRepeatPolicy(BenefitRuleRepeatPolicy.ONCE_PER_MEMBER);
    request.setPoints(300);
    request.setGrantValidityDays(90);
    request.setVersion(0L);
    when(benefitRuleRepository.findById(1L)).thenReturn(Optional.of(visitRule));
    when(benefitRuleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    BenefitRuleResponse response = benefitRuleService.update(1L, request);

    assertThat(response.type()).isEqualTo("VISIT");
    assertThat(response.name()).isEqualTo("来店ボーナス（改）");
    assertThat(response.storeIds()).isEmpty();
    assertThat(response.grantValidityDays()).isEqualTo(90);
  }

  @Test
  @DisplayName("陳腐化した編集フォームの提出は 409 で撥ねられること")
  void staleUpdateIsRejected() {
    BenefitRuleUpdateRequest request = new BenefitRuleUpdateRequest();
    request.setName("来店ボーナス（改）");
    request.setStoreScopeType(StoreScopeType.ALL_STORES);
    request.setRepeatPolicy(BenefitRuleRepeatPolicy.EVERY_TIME);
    request.setPoints(300);
    request.setVersion(1L);
    when(benefitRuleRepository.findById(1L)).thenReturn(Optional.of(visitRule));

    assertThatThrownBy(() -> benefitRuleService.update(1L, request))
        .isInstanceOf(StaleBenefitRuleUpdateException.class);
    assertThat(visitRule.getName()).isEqualTo("来店ボーナス");
  }

  @Test
  @DisplayName("停用は集約の標識を倒し、二度目は撥ねられること")
  void deactivationIsOneWay() {
    when(benefitRuleRepository.findById(1L)).thenReturn(Optional.of(visitRule));

    benefitRuleService.deactivate(1L);

    assertThat(visitRule.getEnabled()).isFalse();
    assertThatThrownBy(() -> benefitRuleService.deactivate(1L))
        .isInstanceOf(InvalidBenefitRuleException.class);
  }
}
