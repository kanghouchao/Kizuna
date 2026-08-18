package com.kizuna.shift.domain;

/**
 * シフトの状態。TENTATIVE=店舗内の下書き、CONFIRMED=確定。CONFIRMED → TENTATIVE の逆行も許す（非公開化に相当する既存操作）。
 *
 * <p>TENTATIVE の負向不変量 3 条: 店外へ公開されない・受注確定の内部検証（hasConfirmedShift）に数えない・変更申請の対象にならない。
 */
public enum ShiftStatus {
  TENTATIVE,
  CONFIRMED
}
