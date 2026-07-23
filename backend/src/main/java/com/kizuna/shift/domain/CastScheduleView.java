package com.kizuna.shift.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 本人（キャスト）ポータル週間スケジュールの読み側 projection。
 *
 * <p>店舗名を内联する。CAST は店舗一覧 API（store_bridge=false 見込み）に依存できないため、店名表示に必要な最小限を ここで解決する。
 */
public interface CastScheduleView {

  LocalDate getWorkDate();

  LocalTime getStartTime();

  LocalTime getEndTime();

  String getStatus();

  Long getStoreId();

  String getStoreName();
}
