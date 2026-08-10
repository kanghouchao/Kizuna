package com.kizuna.point.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointAllocationRepository extends JpaRepository<PointAllocation, Long> {

  /**
   * 指定した加算ロットの消費済み量を、引き当て元ごとに合計して返す。
   *
   * <p>引き当ては減算仕訳の側から {@code entry_id} で束ねられており子側に親の写像を持たないが、消費済み量は引き当て元 （{@code
   * source_entry_id}）だけで求まるため親を辿る必要がない。引き当てが 1 件も無いロットは行として返らない。
   */
  @Query(
      "select a.sourceEntryId as sourceEntryId, sum(a.amount) as consumed"
          + " from com.kizuna.point.domain.PointAllocation a"
          + " where a.sourceEntryId in :sourceEntryIds"
          + " group by a.sourceEntryId")
  List<PointConsumption> findConsumedBySourceEntryIds(
      @Param("sourceEntryIds") Collection<Long> sourceEntryIds);
}
