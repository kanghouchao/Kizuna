package com.kizuna.notificationdelivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.notificationdelivery.domain.DeliveryAttempt;
import com.kizuna.notificationdelivery.domain.DeliveryChannel;
import com.kizuna.notificationdelivery.domain.DeliveryContent;
import com.kizuna.notificationdelivery.domain.DeliveryPurpose;
import com.kizuna.notificationdelivery.domain.DeliveryResolution;
import com.kizuna.notificationdelivery.domain.DeliverySourceType;
import com.kizuna.notificationdelivery.domain.DeliveryStatus;
import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class DeliveryDomainTest {
  private DeliveryContent content(String body) {
    return new DeliveryContent(
        DeliverySourceType.ORDER,
        "00000000-0000-7000-8000-000000000123",
        DeliveryChannel.EMAIL,
        DeliveryPurpose.BUSINESS,
        "  予約確認  ",
        body,
        OffsetDateTime.parse("2026-10-07T09:00:00+09:00"),
        "test-key");
  }

  @Test
  void canonicalPayloadReplaysButDifferentContentCannotReuseKey() {
    var row = NotificationDelivery.draft(7L, content("一行目\r\n二行目"));
    row.requireSame(content("一行目\n二行目"));
    assertThat(row.getAttributedStoreId()).isEqualTo(7L);
    assertThat(row.getSubject()).isEqualTo("予約確認");
    assertThat(row.getScheduledAt().toString()).isEqualTo("2026-10-07T00:00Z");
    assertThatThrownBy(() -> row.requireSame(content("別本文"))).isInstanceOf(ConflictException.class);
  }

  @Test
  void taskIssuedMemberNoticeStartsQueuedAtGroupLevelOnly() {
    var member =
        new DeliveryContent(
            DeliverySourceType.MEMBER,
            "9",
            DeliveryChannel.EMAIL,
            DeliveryPurpose.BUSINESS,
            "期限のお知らせ",
            "本文",
            OffsetDateTime.parse("2026-10-07T09:00:00+09:00"),
            "notice-key");
    var row = NotificationDelivery.queued(null, member, "  定期処理  ");
    assertThat(row.getStatus()).isEqualTo(DeliveryStatus.QUEUED);
    assertThat(row.getReason()).isEqualTo("定期処理");
    assertThat(row.getAttemptCount()).isZero();
    row.dispatch();
    assertThat(row.getAttemptCount()).isEqualTo(1);
    assertThatThrownBy(() -> NotificationDelivery.queued(7L, member, "定期処理"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> NotificationDelivery.queued(null, member, " "))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void sentAndUnknownCannotBeRetriedButDefiniteFailureCan() {
    for (var terminal :
        new DeliveryStatus[] {
          DeliveryStatus.SENT, DeliveryStatus.UNKNOWN, DeliveryStatus.FAILED, DeliveryStatus.BLOCKED
        }) {
      var row = NotificationDelivery.draft(7L, content("本文"));
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

  private NotificationDelivery unknown() {
    var row = NotificationDelivery.draft(7L, content("本文"));
    row.queue(null, "確認済み", false);
    row.dispatch();
    row.sending();
    row.finish(DeliveryStatus.UNKNOWN);
    return row;
  }

  @Test
  void onlyUnknownIsResolvedAndOnlyUndeliveredJudgmentCanBeRetried() {
    var failed = unknown();
    failed.resolve(null, DeliveryResolution.FAILED, "  提供元で未配信を確認  ");
    assertThat(failed.getStatus()).isEqualTo(DeliveryStatus.FAILED);
    assertThat(failed.getAttemptCount()).isEqualTo(2);
    var attempt = DeliveryAttempt.resolution(failed, 9L, "通知担当", OffsetDateTime.now());
    assertThat(attempt.getAttemptNumber()).isEqualTo(2);
    assertThat(attempt.getStatus()).isEqualTo(DeliveryStatus.FAILED);
    assertThat(attempt.getFailureCode()).isEqualTo("MANUAL_RESOLUTION");
    assertThat(attempt.getTaskExecutionId()).isNull();
    assertThat(attempt.getReason()).isEqualTo("提供元で未配信を確認");
    assertThat(attempt.getStartedAt()).isEqualTo(attempt.getFinishedAt());
    assertThatThrownBy(() -> failed.resolve(null, DeliveryResolution.SENT, "重ねて判定"))
        .isInstanceOf(ConflictException.class);
    failed.queue(null, "未送達の判定にもとづき再送", true);
    assertThat(failed.getStatus()).isEqualTo(DeliveryStatus.QUEUED);

    var sent = unknown();
    sent.resolve(null, DeliveryResolution.SENT, "提供元で配信を確認");
    assertThat(sent.getStatus()).isEqualTo(DeliveryStatus.SENT);
    assertThatThrownBy(() -> sent.queue(null, "再送", true)).isInstanceOf(ConflictException.class);

    assertThatThrownBy(() -> unknown().resolve(5L, DeliveryResolution.FAILED, "古い版"))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> unknown().resolve(null, DeliveryResolution.FAILED, " "))
        .isInstanceOf(ServiceException.class);
    var draft = NotificationDelivery.draft(7L, content("本文"));
    assertThatThrownBy(() -> draft.resolve(null, DeliveryResolution.FAILED, "下書き"))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void duplicateAndStaleTransitionsAreRejected() {
    var row = NotificationDelivery.draft(7L, content("本文"));
    assertThatThrownBy(() -> row.queue(10L, "確認", false)).isInstanceOf(ConflictException.class);
    row.queue(null, "確認", false);
    assertThatThrownBy(() -> row.queue(null, "確認", false)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(row::sending).isInstanceOf(ConflictException.class);
  }

  @Test
  void missingDimensionsAndInvalidSchedulesCannotEnterDelivery() {
    assertThatThrownBy(
            () ->
                new DeliveryContent(
                    DeliverySourceType.ORDER,
                    "00000000-0000-7000-8000-000000000001",
                    null,
                    DeliveryPurpose.BUSINESS,
                    "件名",
                    "本文",
                    OffsetDateTime.now(),
                    "a"))
        .isInstanceOf(ServiceException.class);
    var marketing =
        new DeliveryContent(
            DeliverySourceType.MEMBER,
            "1",
            DeliveryChannel.EMAIL,
            DeliveryPurpose.MARKETING,
            "件名",
            "本文",
            OffsetDateTime.now(),
            "a");
    assertThat(NotificationDelivery.draft(null, marketing).getAttributedStoreId()).isNull();
    // 会員宛はグループ級だけ、受注・ゲスト申請は店舗の行だけ。
    assertThatThrownBy(() -> NotificationDelivery.draft(7L, marketing))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> NotificationDelivery.draft(null, content("本文")))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> content(" ")).isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () -> content("本文").validateNewSchedule(OffsetDateTime.parse("2020-01-01T00:00Z")))
        .isInstanceOf(ServiceException.class);
  }
}
