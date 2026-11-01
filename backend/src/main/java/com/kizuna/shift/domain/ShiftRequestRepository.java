package com.kizuna.shift.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftRequestRepository extends StoreScopedRepository<ShiftRequest> {

  /** 親シフトを先にロックし、DBの関連解除前の値を有限ページで確保する。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select r from ShiftRequest r where r.storeId = :storeId and r.shiftId = :shiftId and r.id > :afterId order by r.id")
  List<ShiftRequest> findLinkedForUpdate(
      @Param("storeId") Long storeId,
      @Param("shiftId") String shiftId,
      @Param("afterId") String afterId,
      Limit limit);

  List<ShiftRequest> findByStoreIdAndStatusOrderByCreatedAtAsc(
      Long storeId, ShiftRequestStatus status);

  List<ShiftRequest> findByStoreIdOrderByCreatedAtAsc(Long storeId);

  /**
   * このシフトを生んだ出勤希望（NEW）。承認の shift_id 回写で結ばれ、1 シフトにつき高々 1 本 — 承認は毎回 新しいシフトを作るので、同じシフトを 2 本の NEW
   * が指すことはない。店舗が直接作成した行では空。
   *
   * <p>{@code findFirst} で受けるのは、万一の重複でも 500 ではなく決定的な 1 本を返すためである。
   */
  Optional<ShiftRequest> findFirstByStoreIdAndShiftIdAndTypeOrderByCreatedAtAscIdAsc(
      Long storeId, String shiftId, ShiftRequestType type);

  /** シフトの変更申請履歴。並びは呼出側の Pageable が決める。 */
  Page<ShiftRequest> findByStoreIdAndShiftIdAndType(
      Long storeId, String shiftId, ShiftRequestType type, Pageable pageable);

  // 本人（cast_id 集合、店舗を跨ぐ）の出勤希望履歴を店名込みで返す。where 句に店舗の絞りは書かない —
  // cast_id は当人が所属する店にしか存在しないため、cast_id で本人に限ればそのまま店舗も限られる。
  String HISTORY_SELECT =
      """
      select r.id as id, r.workDate as workDate, r.startTime as startTime, r.endTime as endTime,
             r.note as note, r.type as type, r.status as status, r.storeId as storeId,
             st.name as storeName, r.createdAt as createdAt
      from ShiftRequest r join com.kizuna.store.domain.Store st on st.id = r.storeId
      """;

  String HISTORY_WHERE = " where r.castId in :castIds ";

  /** 本人の出勤希望履歴。並びは呼出側の Pageable が決める。 */
  @Query(
      value = HISTORY_SELECT + HISTORY_WHERE,
      countQuery =
          "select count(r) from ShiftRequest r join com.kizuna.store.domain.Store st"
              + " on st.id = r.storeId"
              + HISTORY_WHERE)
  Page<CastShiftRequestView> findHistoryByCastIds(
      @Param("castIds") List<String> castIds, Pageable pageable);
}
