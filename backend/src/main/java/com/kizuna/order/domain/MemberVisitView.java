package com.kizuna.order.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 会員本人が見る来店 1 件の読み側 projection。
 *
 * <p>来店の正本は帰属記録（{@link OrderAttribution}）で、表示に要る来店日・店舗名・人数・担当は受注への join で導出する。会計金額・利用ポイント・顧客台帳の項目は
 * projection の段階で落ちる。
 *
 * <p>{@code createdAt} は続きの位置（カーソル）を作るためだけに持ち、応答へは出さない。{@code orderId}
 * も同じく応答へは出さず、当該受注の獲得ポイントを台帳から引くためだけに持つ。
 */
public interface MemberVisitView {

  Long getId();

  OffsetDateTime getCreatedAt();

  String getOrderId();

  /** 来店日（受注の業務日）。 */
  LocalDate getVisitedOn();

  String getStoreName();

  Integer getPax();

  /** 担当キャストの表示名。指名も割り当ても無い受注では欠落する。 */
  String getCastName();
}
