package com.kizuna.storage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
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
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class StoredFileAuditTest {
  private final UserRepository users = mock(UserRepository.class);
  private final AuditEventRepository events = mock(AuditEventRepository.class);
  private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
  private final List<AuditEvent> recorded = new ArrayList<>();
  private StoredFileAudit audit;

  @BeforeEach
  void setup() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("operator@example.test", null, List.of()));
    audit =
        new StoredFileAudit(
            new BusinessAudit(users, new AuditWriter(events, Clock.systemUTC())), transactions);
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
    when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    when(events.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              AuditEvent event = call.getArgument(0);
              recorded.add(event);
              return event;
            });
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void recordsWhitelistInIndependentTransaction() {
    audit.recorded(5L, "public/5/a.jpg", "public", 4L, "image/jpeg");

    var definition = ArgumentCaptor.forClass(TransactionDefinition.class);
    verify(transactions).getTransaction(definition.capture());
    assertThat(definition.getValue().getPropagationBehavior())
        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    var event = recorded.getFirst();
    assertThat(event.getAction()).isEqualTo("FILE_UPLOADED");
    assertThat(event.getTargetType()).isEqualTo("STORED_FILE");
    assertThat(event.getTargetId()).isEqualTo("public/5/a.jpg");
    assertThat(event.getStoreId()).isEqualTo(5L);
    assertThat(event.getAfterValues())
        .containsOnly(
            Map.entry("bucket", "public"),
            Map.entry("size", "4"),
            Map.entry("content_type", "image/jpeg"));
  }

  @Test
  void requestWithoutStoreAndContentTypeRecordsNullStoreAndBlankType() {
    audit.recorded(null, "public/platform/a.bin", "public", 0L, null);

    var event = recorded.getFirst();
    assertThat(event.getStoreId()).isNull();
    assertThat(event.getAfterValues()).containsEntry("content_type", "");
  }

  @Test
  void auditFailureIsSwallowedAndRolledBack() {
    when(events.saveAndFlush(any())).thenThrow(new IllegalStateException("audit"));

    assertThatCode(() -> audit.recorded(5L, "public/5/a.jpg", "public", 4L, "image/jpeg"))
        .doesNotThrowAnyException();

    verify(transactions).rollback(any());
  }
}
