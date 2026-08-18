package com.kizuna.store.domain;

/**
 * 店舗に当日実績の行が存在するかを問い合わせる跨モジュールポート。store から shift を直接参照すると Store → Shift → Cast → Store
 * の循環が閉じるため、インターフェースを store 側に置き shift 側が実装する。
 *
 * <p>単一実装のインターフェースは通常避けるが、これは跨モジュール依存を逆転させるためのポートであり、その規約の 明示的な例外にあたる（{@link CompletedOrderCheck}
 * と同型）。
 */
@FunctionalInterface
public interface AttendanceRecordCheck {

  /** 指定店舗に実績行が 1 件でも存在するか。取消済みも数える。 */
  boolean existsForStore(long storeId);
}
