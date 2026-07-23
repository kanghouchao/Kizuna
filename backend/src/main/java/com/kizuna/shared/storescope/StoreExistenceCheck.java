package com.kizuna.shared.storescope;

/**
 * 店舗の実在性を問い合わせる跨モジュールポート。shared は store モジュールへ依存できないため、 インターフェースを shared 側に置き store 側が実装する
 * （店→shared は Modulith の許容方向）。
 *
 * <p>単一実装のインターフェースは通常避けるが、これは跨モジュール依存を逆転させるためのポートであり、その規約の 明示的な例外にあたる。{@link StoreScopeExecutor}
 * が平台側の店舗文脈確立時に授権検査と対で実在性を保証するのに用いる。
 */
@FunctionalInterface
public interface StoreExistenceCheck {

  /** 指定 storeId の店舗が実在するか。 */
  boolean exists(long storeId);
}
