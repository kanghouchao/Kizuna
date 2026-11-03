package com.kizuna.service.application;

import com.kizuna.service.domain.ChargeType;
import com.kizuna.service.domain.ConsentStatus;
import com.kizuna.service.domain.ServiceKind;

/** キャスト本人から見たサービスの条件。受諾の状態と意思版は特殊サービスだけが持ち、他は null。 */
public record OwnServiceCondition(
    String id,
    Long storeId,
    ServiceKind kind,
    String name,
    Integer durationMinutes,
    ChargeType chargeType,
    int price,
    int remuneration,
    long termsVersion,
    ConsentStatus consentStatus,
    Long consentVersion) {}
