package com.kizuna.shift.domain;

/** 出勤希望の状態。PENDING からのみ APPROVED/DECLINED へ遷移でき、処理済みへの再処理は許さない。 */
public enum ShiftRequestStatus {
  PENDING,
  APPROVED,
  DECLINED
}
