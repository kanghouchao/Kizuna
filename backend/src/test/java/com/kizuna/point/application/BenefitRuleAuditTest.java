package com.kizuna.point.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.point.api.dto.BenefitRuleCreateRequest;
import com.kizuna.point.api.dto.BenefitRuleDeactivationRequest;
import com.kizuna.point.api.dto.BenefitRuleMapperImpl;
import com.kizuna.point.api.dto.BenefitRuleUpdateRequest;
import com.kizuna.point.domain.BenefitRule;
import com.kizuna.point.domain.BenefitRuleDefinition;
import com.kizuna.point.domain.BenefitRuleRepeatPolicy;
import com.kizuna.point.domain.BenefitRuleRepository;
import com.kizuna.point.domain.BenefitRuleType;
import com.kizuna.point.domain.InvalidBenefitRuleException;
import com.kizuna.point.domain.StaleBenefitRuleUpdateException;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class BenefitRuleAuditTest {
  private final BenefitRuleRepository rules = mock(BenefitRuleRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final AuditEventRepository events = mock(AuditEventRepository.class);
  private final List<AuditEvent> recorded = new ArrayList<>();
  private BenefitRuleService service;
  private BenefitRule stored;

  @BeforeEach
  void setup() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("operator@example.test", null, List.of()));
    service =
        new BenefitRuleService(
            rules,
            new BenefitRuleMapperImpl(),
            new BusinessAudit(users, new AuditWriter(events, Clock.systemUTC())));
    var actor =
        User.builder()
            .email("operator@example.test")
            .password(UUID.randomUUID().toString())
            .displayName("監査担当")
            .userType(UserType.STAFF)
            .roleIds(Set.of(1L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .build();
    actor.setId(9L);
    when(users.findByEmail(anyString())).thenReturn(Optional.of(actor));
    when(events.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              AuditEvent event = call.getArgument(0);
              recorded.add(event);
              return event;
            });
    when(rules.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              BenefitRule rule = call.getArgument(0);
              if (rule.getId() == null) {
                ReflectionTestUtils.setField(rule, "id", 7L);
                ReflectionTestUtils.setField(rule, "version", 0L);
              }
              return rule;
            });
    stored = rule("来店ボーナス", 500);
    when(rules.findById(7L)).thenReturn(Optional.of(stored));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void creationAuditsGroupWideWithoutFreeText() {
    var request = new BenefitRuleCreateRequest();
    request.setType(BenefitRuleType.VISIT);
    request.setName("秘密の名前");
    request.setStoreScopeType(StoreScopeType.SPECIFIC_STORES);
    request.setStoreIds(Set.of(3L, 5L));
    request.setRepeatPolicy(BenefitRuleRepeatPolicy.EVERY_TIME);
    request.setPoints(500);

    service.create(request);

    assertThat(recorded).hasSize(1);
    var event = recorded.getFirst();
    assertThat(event.getAction()).isEqualTo("BENEFIT_RULE_CREATED");
    assertThat(event.getTargetType()).isEqualTo("BENEFIT_RULE");
    assertThat(event.getTargetId()).isEqualTo("7");
    assertThat(event.getStoreId()).isNull();
    assertThat(event.getBeforeValues()).containsOnlyKeys("exists").containsEntry("exists", "false");
    assertThat(event.getAfterValues())
        .containsEntry("exists", "true")
        .containsEntry("active", "true")
        .containsEntry("version", "0")
        .containsEntry("type", "VISIT")
        .containsEntry("points", "500")
        .containsEntry("store_ids", "3,5");
    assertThat(event.getAfterValues().toString()).doesNotContain("秘密の名前");
  }

  @Test
  void updateAuditsChangedFieldNamesAndNeverTheName() {
    var request = update(0L, "別の名前", 800);

    service.update(7L, request);

    assertThat(recorded).hasSize(1);
    var event = recorded.getFirst();
    assertThat(event.getAction()).isEqualTo("BENEFIT_RULE_UPDATED");
    assertThat(event.getStoreId()).isNull();
    assertThat(event.getBeforeValues()).containsEntry("points", "500");
    assertThat(event.getAfterValues())
        .containsEntry("points", "800")
        .containsEntry("changed_fields", "points,name");
    assertThat(event.getBeforeValues().toString() + event.getAfterValues())
        .doesNotContain("来店ボーナス")
        .doesNotContain("別の名前");
  }

  @Test
  void nameOnlyUpdateStillAudits() {
    service.update(7L, update(0L, "別の名前", 500));
    assertThat(recorded).hasSize(1);
    assertThat(recorded.getFirst().getAfterValues()).containsEntry("changed_fields", "name");
  }

  @Test
  void identicalUpdateLeavesNoAudit() {
    service.update(7L, update(0L, "来店ボーナス", 500));
    assertThat(recorded).isEmpty();
  }

  @Test
  void deactivationAuditsStateFlip() {
    var request = new BenefitRuleDeactivationRequest();
    request.setVersion(0L);

    service.deactivate(7L, request);

    assertThat(recorded).hasSize(1);
    var event = recorded.getFirst();
    assertThat(event.getAction()).isEqualTo("BENEFIT_RULE_DEACTIVATED");
    assertThat(event.getStoreId()).isNull();
    assertThat(event.getBeforeValues()).containsEntry("active", "true");
    assertThat(event.getAfterValues()).containsEntry("active", "false");
  }

  @Test
  void rejectionsLeaveNoAudit() {
    assertThatThrownBy(() -> service.update(7L, update(5L, "x", 800)))
        .isInstanceOf(StaleBenefitRuleUpdateException.class);
    var invalid = update(0L, "x", 0);
    assertThatThrownBy(() -> service.update(7L, invalid))
        .isInstanceOf(InvalidBenefitRuleException.class);
    var stale = new BenefitRuleDeactivationRequest();
    stale.setVersion(9L);
    assertThatThrownBy(() -> service.deactivate(7L, stale))
        .isInstanceOf(StaleBenefitRuleUpdateException.class);
    assertThat(recorded).isEmpty();
  }

  private BenefitRuleUpdateRequest update(Long version, String name, int points) {
    var request = new BenefitRuleUpdateRequest();
    request.setVersion(version);
    request.setName(name);
    request.setStoreScopeType(StoreScopeType.SPECIFIC_STORES);
    request.setStoreIds(Set.of(3L, 5L));
    request.setRepeatPolicy(BenefitRuleRepeatPolicy.EVERY_TIME);
    request.setPoints(points);
    return request;
  }

  private static BenefitRule rule(String name, int points) {
    var rule =
        BenefitRule.define(
            BenefitRuleType.VISIT,
            BenefitRuleDefinition.builder()
                .name(name)
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of(3L, 5L))
                .repeatPolicy(BenefitRuleRepeatPolicy.EVERY_TIME)
                .points(points)
                .build());
    ReflectionTestUtils.setField(rule, "id", 7L);
    ReflectionTestUtils.setField(rule, "version", 0L);
    return rule;
  }
}
