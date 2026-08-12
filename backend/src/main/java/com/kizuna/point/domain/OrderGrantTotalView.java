package com.kizuna.point.domain;

/** 受注 1 件に対する付与の合計。仕訳そのものを外へ出さずに「その受注で何ポイント得たか」だけを渡すための読み側 projection。 */
public interface OrderGrantTotalView {

  String getOrderId();

  Long getTotal();
}
