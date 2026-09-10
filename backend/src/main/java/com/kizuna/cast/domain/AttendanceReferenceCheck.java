package com.kizuna.cast.domain;

import java.util.Collection;
import java.util.Set;

/**
 * キャストが当日実績から参照されているかを問い合わせる跨モジュールポート。shift が cast を参照している向きは変えられないので、 インターフェースを cast 側に置き shift
 * 側が実装する。単一実装のインターフェースを避ける規約の例外にあたる理由は {@code com.kizuna.store.domain.CompletedOrderCheck} に記した通り。
 */
@FunctionalInterface
public interface AttendanceReferenceCheck {

  /** 実績行が参照する指定キャストの ID。取消済みも、そのキャストのシフト経由の参照も数える。 */
  Set<String> findReferencedCastIds(Collection<String> castIds);
}
