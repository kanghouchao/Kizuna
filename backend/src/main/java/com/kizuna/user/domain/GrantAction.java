package com.kizuna.user.domain;

/** 付与履歴の操作種別（付与・変更・停止・再開）。 */
public enum GrantAction {
  /** 新規付与（スタッフ作成時）。 */
  GRANT,
  /** 授権内容の変更（束・店舗集合・精算範囲）。 */
  CHANGE,
  /** 停止（enabled=false）。行は削除せず、過去の実行主体の記録を保持する。 */
  STOP,
  /** 再開（enabled=true）。 */
  RESUME
}
