package com.kizuna.customer.domain;

import java.time.OffsetDateTime;

/** 紐づけ履歴の読み側 projection。実行者の表示名は ID 参照のため JPQL join で取得する。 */
public interface CustomerMemberLinkView {

  String getId();

  String getMemberCode();

  LinkStatus getStatus();

  OffsetDateTime getLinkedAt();

  String getLinkedByName();

  OffsetDateTime getReleasedAt();

  String getReleasedByName();
}
