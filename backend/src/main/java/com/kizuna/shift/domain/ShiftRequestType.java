package com.kizuna.shift.domain;

/**
 * 出勤希望の種別。NEW=新規希望（承認で確定シフトを新規作成）、CHANGE=確定済みシフトへの変更申請（承認で対象シフトを更新）。
 *
 * <p>変更申請は独立した集約ではなく、希望→承認→変更申請と続く単一正本の状態系列の一部としてこの判別軸で扱う。
 */
public enum ShiftRequestType {
  NEW,
  CHANGE
}
