package com.kizuna.shift.domain;

import java.time.LocalTime;

/** 指定日の確定シフトに入っているキャストの読み側 projection。指名候補の提示に必要な最小限のみを持つ。 */
public interface ConfirmedShiftCastView {

  String getCastId();

  String getCastName();

  String getCastPhotoUrl();

  LocalTime getStartTime();

  LocalTime getEndTime();
}
