package com.kizuna.shift.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftRepository
    extends JpaRepository<Shift, String>, JpaSpecificationExecutor<Shift> {

  List<Shift> findByWorkDateBetween(LocalDate from, LocalDate to);

  List<Shift> findByWorkDateAndStatusOrderByStartTimeAsc(LocalDate workDate, String status);

  /**
   * 本人（cast_id 集合、跨店）の週間確定シフトを店名内联で返す。where 句に店舗の絞りは書かない — cast_id は当人が所属する店にしか 存在しないため、cast_id
   * 自限がそのまま店舗自限として機能する（storeFilter は経由しない）。
   */
  @Query(
      """
      select s.workDate as workDate, s.startTime as startTime, s.endTime as endTime,
             s.status as status, s.storeId as storeId, st.name as storeName
      from Shift s join com.kizuna.store.domain.Store st on st.id = s.storeId
      where s.castId in :castIds and s.status = 'CONFIRMED'
        and s.workDate between :from and :to
      order by s.workDate asc, s.startTime asc
      """)
  List<CastScheduleView> findConfirmedSchedule(
      @Param("castIds") List<String> castIds,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);
}
