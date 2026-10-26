package com.kizuna.customer.domain;

import java.time.OffsetDateTime;

/** 会員起点で見る関連 1 区間の読み側 projection。店舗名と顧客名は JPQL join で取る。 */
public interface MemberLinkDirectoryView {

  String getId();

  Long getStoreId();

  String getStoreName();

  String getCustomerId();

  String getCustomerName();

  LinkStatus getStatus();

  LinkReason getReason();

  OffsetDateTime getLinkedAt();

  OffsetDateTime getReleasedAt();

  String getReleaseReason();
}
