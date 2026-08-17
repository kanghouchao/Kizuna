package com.kizuna.customer.domain;

/** 顧客ごとの受注件数の読み側 projection。受注を 1 件も持たない顧客は結果に現れない。 */
public interface CustomerOrderCountView {

  String getCustomerId();

  long getOrderCount();
}
