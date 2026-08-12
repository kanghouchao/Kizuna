package com.kizuna.point.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 会員本人が見る明細 1 行の読み側 projection。
 *
 * <p>台帳の仕訳そのもの（{@link PointEntry}）は渡さない。引き当て・元取引・理由・実行者は運用の内部情報で、本人向けの表示に要らない。
 *
 * <p>{@code createdAt} は続きの位置（カーソル）を作るためだけに持ち、応答へは日付として畳んで出す。
 */
public interface MemberPointEntryView {

  Long getId();

  OffsetDateTime getCreatedAt();

  PointEntryType getEntryType();

  Integer getAmount();

  LocalDate getExpiresOn();

  /** 発生店舗の表示名。失効のような系統イベントと、削除された店舗の仕訳では欠落する。 */
  String getStoreName();
}
