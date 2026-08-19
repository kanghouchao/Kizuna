package com.kizuna.shift.domain;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftRepository
    extends JpaRepository<Shift, String>, JpaSpecificationExecutor<Shift> {

  List<Shift> findByWorkDateBetween(LocalDate from, LocalDate to);

  /** 指定勤務日の確定シフト。欠勤導出の母集合で、公開可否は見ない — 公開可否は店外露出のフィルタであって 状態機械の一部ではない（ADR 0015 の負向不変量）。 */
  List<Shift> findByWorkDateAndStatus(LocalDate workDate, ShiftStatus status);

  /** 公式サイト出勤表の絞り。店外への露出関門は CONFIRMED ∧ 公開可（ADR 0015）なので、状態と公開可否の両方で絞る。 */
  List<Shift> findByWorkDateAndStatusAndPublishedTrueOrderByStartTimeAsc(
      LocalDate workDate, ShiftStatus status);

  /**
   * 本人（cast_id 集合、跨店）の週間確定シフトを店名内联で返す。where 句に店舗の絞りは書かない — cast_id は当人が所属する店にしか 存在しないため、cast_id
   * 自限がそのまま店舗自限として機能する（storeFilter は経由しない）。
   */
  @Query(
      """
      select s.id as id, s.workDate as workDate, s.startTime as startTime, s.endTime as endTime,
             s.status as status, s.storeId as storeId, st.name as storeName
      from Shift s join com.kizuna.store.domain.Store st on st.id = s.storeId
      where s.castId in :castIds and s.status = com.kizuna.shift.domain.ShiftStatus.CONFIRMED
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
      where s.storeId = :storeId and s.workDate = :workDate
        and s.status = com.kizuna.shift.domain.ShiftStatus.CONFIRMED
        and s.published = true
        and c.status = 'ACTIVE'
      order by s.startTime asc, s.castId asc
      """)
  List<ConfirmedShiftCastView> findConfirmedCasts(
      @Param("storeId") Long storeId, @Param("workDate") LocalDate workDate);

  /** 指定店舗・指定キャスト・指定日の確定シフトの有無。指名の妥当性検証に使う（店舗隔離は storeId の明示指定による）。 */
  boolean existsByStoreIdAndCastIdAndWorkDateAndStatus(
      Long storeId, String castId, LocalDate workDate, ShiftStatus status);

  /** 上と同じ問いを店外向けの露出関門（CONFIRMED ∧ 公開可）で答える。 */
  boolean existsByStoreIdAndCastIdAndWorkDateAndStatusAndPublishedTrue(
      Long storeId, String castId, LocalDate workDate, ShiftStatus status);

  /**
   * 現店舗のシフト 1 件を、予実の交差を触る間だけ押さえて引く。
   *
   * <p>実績の記録・訂正はシフトの勤務日を読んで自分の営業日を物化し、シフトの更新・削除・変更申請の適用は 実績の有無を読んで可否を決める。互いに相手の行を書かないので、押さえずに読むと
   * 「実績はまだ無い」と 「勤務日はまだ旧値」が同時に真になり、双方が commit して物化済みの帰属がシフトと食い違う（ADR 0014 が
   * 禁じている状態そのもの）。シフトが両者の共通の親であり、この直列化の単位になる。
   *
   * <p>押さえる順序は ADR 0016（外部キーの連鎖の向き）に従う。実績の記録はキャストを押さえてからここへ来る。
   *
   * <p>この読み口はその取引でのシフトの**最初の**読み込みでなければならない。既に永続化文脈に載った実体へ後から
   * ロックを掛けると獲得が版の照合を伴い、シフトを書きもしない要求が版の進みだけで 409 に落ちる。
   *
   * <p>JPQL で書くのは {@code storeFilter} を効かせるためで、native にすると店舗境界が黙って消える。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from com.kizuna.shift.domain.Shift s where s.id = :id")
  Optional<Shift> findScopedByIdForUpdate(@Param("id") String id);
}
