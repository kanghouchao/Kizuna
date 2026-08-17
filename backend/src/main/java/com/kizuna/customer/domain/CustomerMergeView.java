package com.kizuna.customer.domain;

import java.time.OffsetDateTime;

/**
 * 統合履歴の読み側 projection。実行者と両行の表示名は ID 参照のため JPQL join で取得する。
 *
 * <p>両行を持ったまま返すのは、どちらが「相手」かが問い合わせた顧客によって変わるため。向きの判定と相手の取り出しは 呼出側が行う。
 */
public interface CustomerMergeView {

  String getId();

  String getSurvivingCustomerId();

  String getMergedCustomerId();

  String getSurvivingCustomerName();

  String getMergedCustomerName();

  String getMergedByName();

  OffsetDateTime getMergedAt();

  int getMovedOrderCount();

  int getMovedLinkCount();
}
