package com.kizuna.shift.domain;

/** 出勤希望の種別。NEW=新規希望 / CHANGE=確定済みシフトへの変更申請。状態系列（PENDING→APPROVED/DECLINED）は両者で共通。 */
public enum ShiftRequestKind {
  NEW,
  CHANGE
}
