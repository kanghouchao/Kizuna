package com.kizuna.settings.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.settings.api.dto.SystemConfigMapperImpl;
import com.kizuna.settings.api.dto.SystemConfigUpdateRequest;
import com.kizuna.settings.domain.SystemConfig;
import com.kizuna.settings.domain.SystemConfigRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SystemConfigAuditTest {
  private final SystemConfigRepository configs = mock(SystemConfigRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final AuditEventRepository events = mock(AuditEventRepository.class);
  private final List<AuditEvent> recorded = new ArrayList<>();
  private SystemConfigServiceImpl service;

  @BeforeEach
  void setup() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("operator@example.test", null, List.of()));
    service =
        new SystemConfigServiceImpl(
            configs,
            new SystemConfigMapperImpl(),
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
    when(configs.save(any())).thenAnswer(call -> call.getArgument(0));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "smtp_host",
        "smtp_port",
        "smtp_username",
        "smtp_from",
        "line_channel_id",
        "point_usage_unit",
        "member_rank_gold_visit_count"
      })
  void whitelistedKeysRecordBeforeAndAfter(String key) {
    stored(key, "10", false);

    service.updateConfig(key, request("20"));

    assertThat(recorded).hasSize(1);
    var event = recorded.getFirst();
    assertThat(event.getAction()).isEqualTo("SYSTEM_CONFIG_UPDATED");
    assertThat(event.getTargetType()).isEqualTo("SYSTEM_CONFIG");
    assertThat(event.getTargetId()).isEqualTo(key);
    assertThat(event.getStoreId()).isNull();
    assertThat(event.getBeforeValues()).containsOnly(Map.entry("config_value", "10"));
    assertThat(event.getAfterValues()).containsOnly(Map.entry("config_value", "20"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"smtp_password", "line_channel_secret", "unknown_key"})
  void secretAndUnknownKeysRecordChangedFlagOnly(String key) {
    stored(key, "old-secret-value", false);

    service.updateConfig(key, request("new-secret-value"));

    assertThat(recorded).hasSize(1);
    var event = recorded.getFirst();
    assertThat(event.getBeforeValues()).isEmpty();
    assertThat(event.getAfterValues()).containsOnly(Map.entry("changed", "true"));
    assertThat(event.toString()).doesNotContain("secret-value");
  }

  @Test
  void flaggedSecretNeverRecordsValueEvenIfKeyIsListed() {
    stored("smtp_host", "old-secret-value", true);

    service.updateConfig("smtp_host", request("new-secret-value"));

    var event = recorded.getFirst();
    assertThat(event.getAfterValues()).containsOnly(Map.entry("changed", "true"));
    assertThat(event.getBeforeValues()).isEmpty();
  }

  @Test
  void identicalOrBlankToBlankUpdateLeavesNoAudit() {
    stored("smtp_host", "same", false);
    service.updateConfig("smtp_host", request("same"));
    service.updateConfig("smtp_host", request(null));
    stored("smtp_from", null, false);
    service.updateConfig("smtp_from", request(""));
    assertThat(recorded).isEmpty();
  }

  @Test
  void rejectionsLeaveNoAuditAndDoNotLeakValue() {
    stored("smtp_port", "25", false);
    when(configs.findByConfigKey("smtp_port"))
        .thenReturn(
            Optional.of(
                SystemConfig.builder()
                    .configKey("smtp_port")
                    .configValue("25")
                    .valueType("NUMBER")
                    .build()));
    assertThatThrownBy(() -> service.updateConfig("smtp_port", request("leaky-abc")))
        .isInstanceOf(ServiceException.class)
        .hasMessageNotContaining("leaky-abc");
    when(configs.findByConfigKey("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.updateConfig("missing", request("x")))
        .isInstanceOf(NotFoundException.class);
    assertThat(recorded).isEmpty();
  }

  private void stored(String key, String value, boolean secret) {
    when(configs.findByConfigKey(key))
        .thenReturn(
            Optional.of(
                SystemConfig.builder().configKey(key).configValue(value).secret(secret).build()));
  }

  private static SystemConfigUpdateRequest request(String value) {
    return SystemConfigUpdateRequest.builder().configValue(value).build();
  }
}
