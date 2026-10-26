package com.kizuna.shared.storescope;

/**
 * 店舗の実在性を問い合わせるポート。shared は store モジュールへ依存できないため、インターフェースを shared 側に置き store 側が実装する （store → shared
 * は Modulith の許容方向）。
 *
 * <p>単一実装のインターフェースは通常避けるが、これはモジュール間の依存を逆転させるためのポートであり、その規約の明示的な例外にあたる。
 */
@FunctionalInterface
public interface StoreExistenceCheck {

  /** 指定 storeId の店舗が実在するか。 */
  boolean exists(long storeId);
}
