package com.kizuna.cast.domain;

import java.util.Collection;
import java.util.Set;

/** 応募者からの在籍登録による削除制約を cast 側から問い合わせるポート。依存方向を保つため recruitment 側が実装する。 */
@FunctionalInterface
public interface ApplicantReferenceCheck {
  Set<String> findReferencedCastIds(Collection<String> castIds);
}
