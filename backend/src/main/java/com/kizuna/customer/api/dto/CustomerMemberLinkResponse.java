package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.CustomerMemberLink;
import java.time.OffsetDateTime;

/** 現に有効な会員紐づけの応答。JSON キーは Jackson 設定により snake_case（member_code）。 */
public record CustomerMemberLinkResponse(
    String id, boolean linked, String memberCode, OffsetDateTime linkedAt) {
  public static CustomerMemberLinkResponse of(CustomerMemberLink link) {
    return new CustomerMemberLinkResponse(
        link.getId(), true, link.getMemberCode(), link.getLinkedAt());
  }
}
