package com.kizuna.point.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointEntryRepository extends JpaRepository<PointEntry, Long> {

  /** 会員の加算ロット（残高照会用、ロックなし）。エンティティ名は HQL の予約語衝突を避けるため FQCN で参照する。 */
  @Query(
      "select e from com.kizuna.point.domain.PointEntry e where e.memberId = :memberId"
          + " and e.amount > 0")
  List<PointEntry> findCredits(@Param("memberId") Long memberId);

  /**
   * 会員の加算ロットを悲観排他ロック（SELECT ... FOR UPDATE）付きで取得する。
   *
   * <p>「残高は 0 未満にならない」は行を跨いだ合計の不変量で、DB 制約では守れない。同一会員の消費を直列化することで、 並行する 2
   * つの消費が同じ残りを二重に引き当てるのを防ぐ。読むだけの経路は {@link #findCredits} を使う。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select e from com.kizuna.point.domain.PointEntry e where e.memberId = :memberId"
          + " and e.amount > 0")
  List<PointEntry> findCreditsForUpdate(@Param("memberId") Long memberId);
}
