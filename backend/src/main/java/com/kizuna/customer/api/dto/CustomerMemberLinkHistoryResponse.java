package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.LinkStatus;
import java.time.OffsetDateTime;

/** 会員紐づけ履歴 1 件の応答。JSON キーは Jackson 設定により snake_case（member_code）。 */
public record CustomerMemberLinkHistoryResponse(
    String id,
    String memberCode,
    LinkStatus status,
    OffsetDateTime linkedAt,
    String linkedByName,
    OffsetDateTime releasedAt,
    String releasedByName) {}
