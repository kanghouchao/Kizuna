package com.kizuna.shift.domain;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftRequestRepository extends JpaRepository<ShiftRequest, String> {

  List<ShiftRequest> findByStatusOrderByCreatedAtAsc(ShiftRequestStatus status);

  List<ShiftRequest> findAllByOrderByCreatedAtAsc();

  /**
   * シフトへ結ばれた申請（NEW の出生・CHANGE の履歴）を古い順に。系列照会が背骨（shift_id）を辿る唯一の口である。
   *
   * <p>createdAt は同一秒に並ぶ提出で同値になりうるので、一意な副キー id を重ねて全順序にする。
   */
  List<ShiftRequest> findByShiftIdOrderByCreatedAtAscIdAsc(String shiftId);

  // 本人（cast_id 集合、跨店）の出勤希望履歴を店名埋め込みで返す。where 句に店舗の絞りは書かない —
  // cast_id は当人が所属する店にしか存在しないため、cast_id 自限がそのまま店舗自限として機能する
  // （storeFilter は経由しない）。
  String HISTORY_SELECT =
      """
      select r.id as id, r.workDate as workDate, r.startTime as startTime, r.endTime as endTime,
             r.note as note, r.type as type, r.status as status, r.storeId as storeId,
             st.name as storeName, r.createdAt as createdAt
      from ShiftRequest r join com.kizuna.store.domain.Store st on st.id = r.storeId
      """;

  String HISTORY_WHERE = " where r.castId in :castIds ";

  // 新しい申請が先頭。createdAt は同一秒に並ぶ提出で同値になりうるので、一意な副キー id を重ねて
  // 全順序にする。副キーが無いとページの境界で行が重複または欠落する。カーソルの比較も同じ組で行う。
  String HISTORY_ORDER = " order by r.createdAt desc, r.id desc";

  /** 本人の出勤希望履歴の先頭。 */
  @Query(HISTORY_SELECT + HISTORY_WHERE + HISTORY_ORDER)
  List<CastShiftRequestView> findHistoryByCastIds(
      @Param("castIds") List<String> castIds, Limit limit);

  /**
   * 本人の出勤希望履歴の続き。渡された位置より後ろ（＝より古い側）だけを返す。
   *
   * <p>id は Snowflake の文字列で、比較も文字列の順序で行う。求めるのは全順序の決定性だけなので、 並び（{@link #HISTORY_ORDER}）と同じ順序であれば足りる。
   */
  @Query(
      HISTORY_SELECT
          + HISTORY_WHERE
          + """
            and (r.createdAt < :cursorCreatedAt
                 or (r.createdAt = :cursorCreatedAt and r.id < :cursorId))
            """
          + HISTORY_ORDER)
  List<CastShiftRequestView> findHistoryByCastIdsAfter(
      @Param("castIds") List<String> castIds,
      @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
      @Param("cursorId") String cursorId,
      Limit limit);
}
