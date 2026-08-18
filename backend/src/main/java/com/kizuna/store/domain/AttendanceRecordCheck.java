package com.kizuna.store.domain;

/**
 * 店舗に当日実績の行が存在するかを問い合わせる跨モジュールポート。store から shift を直接参照すると Store → Shift → Cast → Store
 * の循環が閉じるため、インターフェースを store 側に置き shift 側が実装する。単一実装のインターフェースを避ける規約の例外にあたる 理由は {@link
 * CompletedOrderCheck} に記した通り。
 */
@FunctionalInterface
public interface AttendanceRecordCheck {

  /** 指定店舗に実績行が 1 件でも存在するか。取消済みも数える。 */
  boolean existsForStore(long storeId);
}
