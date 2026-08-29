package com.kizuna.point.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointAllocationRepository extends JpaRepository<PointAllocation, Long> {

  /**
   * 指定した加算ロットの消費済み量を、引き当て元ごとに合計して返す。引き当てが 1 件も無いロットは行として返らない。
   *
   * <p>引き当ては減算仕訳の側から {@code entry_id} で束ねられており子側に親の写像を持たない。親を辿るのは<b>向き</b>を
   * 読むためで、利用取消が持つ引き当ては消費ではなく元のロットへの<b>返し</b>である。合計を符号付きにすることで、 逆転された利用の消費が元のロットの残りへそのまま戻る。
   */
  @Query(
      "select a.sourceEntryId as sourceEntryId,"
          + " sum(case when e.entryType = com.kizuna.point.domain.PointEntryType.USE_CANCEL"
          + " then -a.amount else a.amount end) as consumed"
          + " from com.kizuna.point.domain.PointEntry e join e.allocations a"
          + " where a.sourceEntryId in :sourceEntryIds"
          + " group by a.sourceEntryId")
  List<PointConsumption> findConsumedBySourceEntryIds(
      @Param("sourceEntryIds") Collection<Long> sourceEntryIds);
}
