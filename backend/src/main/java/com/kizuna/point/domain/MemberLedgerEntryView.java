package com.kizuna.point.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 職員が見る明細 1 行の読み側 projection。本人向けと違い理由と実行者の表示名を持つ。引き当て・元取引は持たない。 */
public interface MemberLedgerEntryView {

  Long getId();

  OffsetDateTime getCreatedAt();

  PointEntryType getEntryType();

  Integer getAmount();

  LocalDate getExpiresOn();

  String getReason();

  /** 発生店舗の表示名。失効のような系統イベントと削除された店舗の仕訳では欠落する。 */
  String getStoreName();

  /** 実行者の表示名。系統処理と、利用者の行が消えた仕訳では欠落する。 */
  String getActorName();
}
