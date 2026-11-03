package com.kizuna.customer.domain;

/** 連絡先の識別と値だけの投影。一覧・詳細・見比べで優先連絡先や一致した連絡先を並べるのに使う。 */
public record ContactSummary(String id, ContactType type, String value) {
  public static ContactSummary from(CustomerContact c) {
    return new ContactSummary(c.getId(), c.getType(), c.getValue());
  }
}
