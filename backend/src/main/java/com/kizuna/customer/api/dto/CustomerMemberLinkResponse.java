package com.kizuna.customer.api.dto;

import java.time.OffsetDateTime;

/** 現に有効な会員紐づけの応答。JSON キーは Jackson 設定により snake_case（member_code）。 */
public record CustomerMemberLinkResponse(
    boolean linked, String memberCode, OffsetDateTime linkedAt) {}
