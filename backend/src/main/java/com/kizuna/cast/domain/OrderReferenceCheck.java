package com.kizuna.cast.domain;

import java.util.Collection;
import java.util.Set;

/** 受注・予約申請による削除制約を cast 側から問い合わせるポート。依存方向を保つため order 側が実装する。 */
@FunctionalInterface
public interface OrderReferenceCheck {
  Set<String> findReferencedCastIds(Collection<String> castIds);
}
