package com.kizuna.cast.domain;

/**
 * キャストが当日実績から参照されているかを問い合わせる跨モジュールポート。shift が cast を参照している向きは 変えられないので、インターフェースを cast 側に置き shift
 * 側が実装する。
 *
 * <p>単一実装のインターフェースは通常避けるが、これは跨モジュール依存を逆転させるためのポートであり、その規約の 明示的な例外にあたる（{@code CompletedOrderCheck}
 * と同型）。
 */
@FunctionalInterface
public interface AttendanceReferenceCheck {

  /** 指定キャストを参照する実績行が 1 件でも存在するか。取消済みも数える。 */
  boolean existsForCast(String castId);
}
