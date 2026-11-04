package com.kizuna.notificationdelivery.api.dto;

import com.kizuna.notificationdelivery.domain.DeliveryChannel;
import com.kizuna.notificationdelivery.domain.DeliveryContent;
import com.kizuna.notificationdelivery.domain.DeliveryPurpose;
import com.kizuna.notificationdelivery.domain.DeliverySourceType;
import com.kizuna.shared.exception.ServiceException;
import java.time.OffsetDateTime;

public record DeliveryRequest(
    DeliverySourceType sourceType,
    String sourceId,
    DeliveryChannel channel,
    DeliveryPurpose purpose,
    String subject,
    String body,
    OffsetDateTime scheduledAt,
    String dedupeKey) {
  /** 販促の作成口は消費者が決まるまで閉じる。基盤は販促を扱えるが、職員の HTTP からは業務連絡だけを受ける。 */
  public DeliveryContent content() {
    if (purpose == DeliveryPurpose.MARKETING) throw new ServiceException("作成できる通知は業務連絡だけです");
    return new DeliveryContent(
        sourceType, sourceId, channel, purpose, subject, body, scheduledAt, dedupeKey);
  }
}
