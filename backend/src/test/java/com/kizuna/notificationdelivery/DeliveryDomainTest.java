package com.kizuna.notificationdelivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.notificationdelivery.domain.DeliveryContent;
import com.kizuna.notificationdelivery.domain.DeliveryStatus;
import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class DeliveryDomainTest {
  private DeliveryContent content(String body) {
    return new DeliveryContent(
        DeliveryContent.SourceType.ORDER,
        "123",
        "EMAIL",
        "BUSINESS",
        "  予約確認  ",
        body,
        OffsetDateTime.parse("2026-10-07T09:00:00+09:00"),
        "test-key");
  }

  @Test
  void canonicalPayloadReplaysButDifferentContentCannotReuseKey() {
    var row = NotificationDelivery.draft(content("一行目\r\n二行目"));
    row.requireSame(content("一行目\n二行目"));
    assertThat(row.getSubject()).isEqualTo("予約確認");
    assertThat(row.getScheduledAt().toString()).isEqualTo("2026-10-07T00:00Z");
    assertThatThrownBy(() -> row.requireSame(content("別本文"))).isInstanceOf(ConflictException.class);
  }

  @Test
  void sentAndUnknownCannotBeRetriedButDefiniteFailureCan() {
    for (var terminal :
        new DeliveryStatus[] {
          DeliveryStatus.SENT, DeliveryStatus.UNKNOWN, DeliveryStatus.FAILED, DeliveryStatus.BLOCKED
        }) {
      var row = NotificationDelivery.draft(content("本文"));
      row.queue(null, "確認済み", false);
      row.dispatch();
      row.sending();
      row.finish(terminal);
      if (terminal == DeliveryStatus.SENT || terminal == DeliveryStatus.UNKNOWN)
        assertThatThrownBy(() -> row.queue(null, "再試行", true))
            .isInstanceOf(ConflictException.class);
      else {
        row.queue(null, "再試行", true);
        row.dispatch();
        assertThat(row.getAttemptCount()).isEqualTo(2);
      }
    }
  }

  @Test
  void duplicateAndStaleTransitionsAreRejected() {
    var row = NotificationDelivery.draft(content("本文"));
    assertThatThrownBy(() -> row.queue(10L, "確認", false)).isInstanceOf(ConflictException.class);
    row.queue(null, "確認", false);
    assertThatThrownBy(() -> row.queue(null, "確認", false)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(row::sending).isInstanceOf(ConflictException.class);
  }

  @Test
  void unsupportedChannelsAndInvalidSchedulesCannotEnterDelivery() {
    assertThatThrownBy(
            () ->
                new DeliveryContent(
                    DeliveryContent.SourceType.ORDER,
                    "1",
                    "SMS",
                    "BUSINESS",
                    "件名",
                    "本文",
                    OffsetDateTime.now(),
                    "a"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> content(" ")).isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () -> content("本文").validateNewSchedule(OffsetDateTime.parse("2020-01-01T00:00Z")))
        .isInstanceOf(ServiceException.class);
  }
}
