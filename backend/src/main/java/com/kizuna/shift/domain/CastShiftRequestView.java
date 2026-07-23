package com.kizuna.shift.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * 本人（キャスト）ポータル出勤希望履歴の読み側 projection。
 *
 * <p>店舗名を埋め込む。CAST は店舗コンソール資格を持たず店舗一覧 API に依存できないため、店名表示に必要な最小限を ここで解決する。
 */
public interface CastShiftRequestView {

  String getId();

  LocalDate getWorkDate();

  LocalTime getStartTime();

  LocalTime getEndTime();

  String getNote();

  ShiftRequestStatus getStatus();

  Long getStoreId();

  String getStoreName();

  OffsetDateTime getCreatedAt();
}
