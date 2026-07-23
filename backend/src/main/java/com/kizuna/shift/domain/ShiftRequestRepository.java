package com.kizuna.shift.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftRequestRepository extends JpaRepository<ShiftRequest, String> {

  List<ShiftRequest> findByStatusOrderByCreatedAtAsc(ShiftRequestStatus status);

  List<ShiftRequest> findAllByOrderByCreatedAtAsc();

  /**
   * 本人（cast_id 集合、跨店）の出勤希望履歴を店名内联で返す。where 句に店舗の絞りは書かない — cast_id は当人が所属する店にしか
   * 存在しないため、cast_id 自限がそのまま店舗自限として機能する（storeFilter は経由しない）。
   */
  @Query(
      """
      select r.id as id, r.workDate as workDate, r.startTime as startTime, r.endTime as endTime,
             r.note as note, r.status as status, r.storeId as storeId, st.name as storeName,
             r.createdAt as createdAt
      from ShiftRequest r join com.kizuna.store.domain.Store st on st.id = r.storeId
      where r.castId in :castIds
      order by r.createdAt desc
      """)
  List<CastShiftRequestView> findHistoryByCastIds(@Param("castIds") List<String> castIds);
}
