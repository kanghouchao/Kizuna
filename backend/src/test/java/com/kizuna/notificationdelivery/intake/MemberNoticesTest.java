package com.kizuna.notificationdelivery.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.notificationdelivery.application.DeliveryAudit;
import com.kizuna.notificationdelivery.domain.DeliveryChannel;
import com.kizuna.notificationdelivery.domain.DeliveryPurpose;
import com.kizuna.notificationdelivery.domain.DeliverySourceType;
import com.kizuna.notificationdelivery.domain.DeliveryStatus;
import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import com.kizuna.notificationdelivery.domain.NotificationDeliveryRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MemberNoticesTest {
  private final NotificationDeliveryRepository deliveries =
      mock(NotificationDeliveryRepository.class);
  private final DeliveryAudit audit = mock(DeliveryAudit.class);
  private final MemberNotices notices = new MemberNotices(deliveries, audit);
  private final AuditActor actor = new AuditActor(8L, "SERVICE", "案内処理");
  private final MemberNotices.Notice notice =
      new MemberNotices.Notice(9L, "件名", "本文", "point-expiry-notice:9:2026-10-14");

  @Test
  void issuesAQueuedGroupLevelBusinessEmailAndAuditsIt() {
    when(deliveries.findByAttributedStoreIdIsNullAndDedupeKey(notice.dedupeKey()))
        .thenReturn(Optional.empty());
    when(deliveries.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    assertThat(notices.queueBusinessEmail(actor, notice)).isTrue();
    var row = ArgumentCaptor.forClass(NotificationDelivery.class);
    verify(deliveries).saveAndFlush(row.capture());
    var saved = row.getValue();
    assertThat(saved.getAttributedStoreId()).isNull();
    assertThat(saved.getSourceType()).isEqualTo(DeliverySourceType.MEMBER);
    assertThat(saved.getSourceId()).isEqualTo("9");
    assertThat(saved.getChannel()).isEqualTo(DeliveryChannel.EMAIL);
    assertThat(saved.getPurpose()).isEqualTo(DeliveryPurpose.BUSINESS);
    assertThat(saved.getStatus()).isEqualTo(DeliveryStatus.QUEUED);
    verify(audit).append(eq(saved), eq(actor), eq("NOTIFICATION_CREATED"));
  }

  @Test
  void existingKeySkipsWithoutComparingContent() {
    when(deliveries.findByAttributedStoreIdIsNullAndDedupeKey(notice.dedupeKey()))
        .thenReturn(Optional.of(mock(NotificationDelivery.class)));
    assertThat(notices.queueBusinessEmail(actor, notice)).isFalse();
    verify(deliveries, never()).saveAndFlush(any());
    verifyNoInteractions(audit);
  }
}
