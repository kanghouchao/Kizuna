package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.audit.recording.AuditChange;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BusinessAuditTest {
  @Test
  void recordsTheActorResolvedFromTheUserIdAndRejectsAnUnknownId() {
    var users = mock(UserRepository.class);
    var writer = mock(AuditWriter.class);
    var audit = new BusinessAudit(users, writer);
    when(users.findById(404L)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                audit.record(
                    404L, null, "ROLE_CHANGED", "ROLE", "1", null, null, Map.of(), Map.of()))
        .isInstanceOf(StaleSessionException.class);
    verify(writer, never()).append(any());
    var actor =
        User.builder()
            .email("operator@kizuna.test")
            .password("encoded")
            .displayName("監査担当")
            .userType(UserType.STAFF)
            .enabled(true)
            .roleIds(Set.of(1L))
            .storeIds(Set.of())
            .build();
    actor.setId(10L);
    when(users.findById(10L)).thenReturn(Optional.of(actor));
    audit.record(
        10L,
        null,
        "ROLE_CHANGED",
        "ROLE",
        "1",
        null,
        null,
        Map.of("role_ids", "1"),
        Map.of("role_ids", "2"));
    var event = ArgumentCaptor.forClass(AuditChange.class);
    verify(writer).append(event.capture());
    assertThat(event.getValue().actor()).isEqualTo(new AuditActor(10L, "STAFF", "監査担当"));
    assertThat(event.getValue().afterValues())
        .containsExactlyInAnyOrderEntriesOf(Map.of("role_ids", "2"));
  }

  @Test
  void largeExistingGrantSetsAreRetainedWithoutTruncation() {
    var stores = LongStream.rangeClosed(1, 2000).boxed().collect(Collectors.toSet());
    var user =
        User.builder()
            .displayName("多数店舗の担当")
            .userType(UserType.SERVICE)
            .enabled(true)
            .roleIds(Set.of(1L))
            .storeIds(stores)
            .build();
    var actor = new AuditActor(1L, "STAFF", "監査担当");
    var event =
        new AuditChange(
            actor,
            null,
            "SERVICE_ID_GRANTS_CHANGED",
            "SERVICE_ID",
            "2",
            null,
            null,
            BusinessAudit.grants(user),
            Map.of());
    assertThat(event.beforeValues().get("store_ids").split(",")).hasSize(2000);
    assertThat(event.beforeValues().get("store_ids")).endsWith(",2000");
  }
}
