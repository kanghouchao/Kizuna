package com.kizuna.user.domain;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmergencyElevationRepository extends JpaRepository<EmergencyElevation, Long> {

  /** 自然失効は status を書き換えないため、「まだ有効」は期限の述語まで含めて初めて成立する。 */
  List<EmergencyElevation> findByActivatedByAndStatusAndExpiresAtAfter(
      Long activatedBy, EmergencyElevationStatus status, OffsetDateTime expiresAfter);

  // 履歴一覧。発動者・店舗は内部結合でよい — どちらも NO ACTION の外部キーが行の存在を保証し、
  // 発動記録を残したまま親だけが消えることはない。撤回者は有効な記録で null なので外部結合にする。
  String HISTORY_SELECT =
      """
      select e.id as id, u.displayName as activatorName,
             e.targetStoreId as targetStoreId, st.name as storeName,
             e.reason as reason, e.activatedAt as activatedAt, e.expiresAt as expiresAt,
             e.status as status, r.displayName as revokerName, e.revokedAt as revokedAt
      from com.kizuna.user.domain.EmergencyElevation e
        join com.kizuna.user.domain.PlatformUser u on u.id = e.activatedBy
        join com.kizuna.store.domain.Store st on st.id = e.targetStoreId
        left join com.kizuna.user.domain.PlatformUser r on r.id = e.revokedBy
      """;

  // 新しい発動から。カーソルの比較（下記 AFTER 条件）はこの並びと同じ列の組で行う。
  String HISTORY_ORDER = " order by e.activatedAt desc, e.id desc";

  /** 発動履歴の先頭。並びは発動時刻の降順に一意な副キー id を重ねて全順序にする。 */
  @Query(HISTORY_SELECT + HISTORY_ORDER)
  List<EmergencyElevationView> findHistoryViews(Limit limit);

  /** 発動履歴の続き。渡された位置より後ろ（＝より古い側）だけを返す。 */
  @Query(
      HISTORY_SELECT
          + """
            where (e.activatedAt < :cursorActivatedAt
                   or (e.activatedAt = :cursorActivatedAt and e.id < :cursorId))
            """
          + HISTORY_ORDER)
  List<EmergencyElevationView> findHistoryViewsAfter(
      @Param("cursorActivatedAt") OffsetDateTime cursorActivatedAt,
      @Param("cursorId") Long cursorId,
      Limit limit);
}
