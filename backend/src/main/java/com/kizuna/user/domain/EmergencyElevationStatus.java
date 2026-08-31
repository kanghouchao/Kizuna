package com.kizuna.user.domain;

/** 緊急昇格の記録の状態。期限切れは状態として持たない（倒す機構が無く、期限は時刻の比較で判じる）。 */
public enum EmergencyElevationStatus {
  /** 有効。期限内であれば昇格が効いている。 */
  ACTIVE,
  /** 撤回済み。期限を待たず人手が倒した記録で、行は削除しない。 */
  REVOKED
}
