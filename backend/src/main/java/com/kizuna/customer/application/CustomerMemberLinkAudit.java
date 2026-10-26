package com.kizuna.customer.application;

import com.kizuna.customer.domain.CustomerMemberLink;
import java.util.Map;
import java.util.Objects;

/** 関連の監査記録の前後像。紐づけ・解除・退会による解除が同じ形で残すための単一の定義。 */
final class CustomerMemberLinkAudit {

  static final String RELEASED = "CUSTOMER_MEMBER_LINK_RELEASED";

  private CustomerMemberLinkAudit() {}

  static Map<String, String> snapshot(CustomerMemberLink link) {
    return Map.of(
        "customer_id", link.getCustomerId(),
        "member_id", Objects.toString(link.getMemberId(), ""),
        "status", link.getStatus().name(),
        "reason", link.getReason().name(),
        "version", Objects.toString(link.getVersion(), ""),
        "linked_by", Objects.toString(link.getLinkedBy(), ""),
        "linked_at", Objects.toString(link.getLinkedAt(), ""),
        "released_by", Objects.toString(link.getReleasedBy(), ""),
        "released_at", Objects.toString(link.getReleasedAt(), ""));
  }
}
