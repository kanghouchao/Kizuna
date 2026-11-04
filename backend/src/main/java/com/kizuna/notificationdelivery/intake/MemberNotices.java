package com.kizuna.notificationdelivery.intake;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.notificationdelivery.application.DeliveryAudit;
import com.kizuna.notificationdelivery.domain.DeliveryChannel;
import com.kizuna.notificationdelivery.domain.DeliveryContent;
import com.kizuna.notificationdelivery.domain.DeliveryPurpose;
import com.kizuna.notificationdelivery.domain.DeliverySourceType;
import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import com.kizuna.notificationdelivery.domain.NotificationDeliveryRepository;
import com.kizuna.shared.persistence.Timestamps;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員宛の業務メールを送信待ちで起票する。授権は呼出側の処理が済ませ、連絡可否は見ない — 送信の定期処理が送る直前に判定する。
 * 同じ要求キーの行があれば内容を比べずに何もしない。文案が実行時点の残高を含み、同じ日の再実行で内容が変わりうるため。
 */
@Service
@RequiredArgsConstructor
public class MemberNotices {
  private static final String REASON = "業務の定期処理による起票";
  private final NotificationDeliveryRepository deliveries;
  private final DeliveryAudit audit;

  public record Notice(long memberId, String subject, String body, String dedupeKey) {}

  /** 起票したら true、同じ要求キーの行が既にあれば false。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean queueBusinessEmail(AuditActor actor, Notice notice) {
    var content =
        new DeliveryContent(
            DeliverySourceType.MEMBER,
            Long.toString(notice.memberId()),
            DeliveryChannel.EMAIL,
            DeliveryPurpose.BUSINESS,
            notice.subject(),
            notice.body(),
            Timestamps.now(),
            notice.dedupeKey());
    if (deliveries.findByAttributedStoreIdIsNullAndDedupeKey(content.dedupeKey()).isPresent())
      return false;
    var row = deliveries.saveAndFlush(NotificationDelivery.queued(null, content, REASON));
    audit.append(row, actor, "NOTIFICATION_CREATED");
    return true;
  }
}
