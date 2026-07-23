package com.kizuna.cast.domain;

/** 本人（キャスト）ポータルの所属店舗セレクタ用 projection（出勤希望提出フォームの店舗選択に使う）。 */
public interface CastStoreView {

  Long getStoreId();

  String getStoreName();
}
