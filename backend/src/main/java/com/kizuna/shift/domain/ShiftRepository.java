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
      select s.id as id, s.workDate as workDate, s.startTime as startTime, s.endTime as endTime,
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

  /**
   * 指定店舗・指定日の確定シフトに入っている ACTIVE キャストを返す。
   *
   * <p>会員ポータルからの参照は店舗文脈（{@code @StoreScoped}）を持たず storeFilter が働かないため、where 句の {@code s.storeId}
   * が唯一の店舗隔離境界である。並びは一意な副キー cast_id まで固定する。
   */
  @Query(
      """
      select s.castId as castId, c.name as castName, c.photoUrl as castPhotoUrl,
             s.startTime as startTime, s.endTime as endTime
      from Shift s join com.kizuna.cast.domain.Cast c on c.id = s.castId
      where s.storeId = :storeId and s.workDate = :workDate and s.status = 'CONFIRMED'
        and c.status = 'ACTIVE'
      order by s.startTime asc, s.castId asc
      """)
  List<ConfirmedShiftCastView> findConfirmedCasts(
      @Param("storeId") Long storeId, @Param("workDate") LocalDate workDate);

  /** 指定店舗・指定キャスト・指定日の確定シフトの有無。指名の妥当性検証に使う（店舗隔離は storeId の明示指定による）。 */
  boolean existsByStoreIdAndCastIdAndWorkDateAndStatus(
      Long storeId, String castId, LocalDate workDate, String status);
}
