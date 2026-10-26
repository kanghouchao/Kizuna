package com.kizuna.member.domain;

import java.time.OffsetDateTime;

/** 職員が見る会員一覧 1 行の読み側 projection。電子メール・表示名は利用者行から JPQL join で取る。 */
public interface MemberSearchView {

  Long getId();

  String getMemberCode();

  MemberRank getRank();

  OffsetDateTime getWithdrawnAt();

  OffsetDateTime getCreatedAt();

  String getEmail();

  String getDisplayName();
}
