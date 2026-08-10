package com.kizuna.store.domain;

/**
 * 店舗に完了済みの受注が存在するかを問い合わせる跨モジュールポート。store から order を直接参照すると Store → Order → Cast → Store
 * の循環が閉じるため、インターフェースを store 側に置き order 側が実装する。
 *
 * <p>単一実装のインターフェースは通常避けるが、これは跨モジュール依存を逆転させるためのポートであり、その規約の 明示的な例外にあたる（{@code StoreExistenceCheck}
 * と同型）。
 */
@FunctionalInterface
public interface CompletedOrderCheck {

  /** 指定店舗に完了済みの受注が 1 件でも存在するか。 */
  boolean existsForStore(long storeId);
}
