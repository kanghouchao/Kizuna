package com.kizuna.order.domain;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderAttributionRepository extends JpaRepository<OrderAttribution, Long> {

  // 会員本人の来店一覧。帰属記録が正本で、表示に要る属性は受注へ join して導出する（ADR 0003 と同じ扱い。
  // 帰属記録は台帳と同じく platform 帰属なので同 ADR が名指す店舗作用域エンティティそのものではない）。店舗は
  // 内部結合でよい — 完了受注を持つ店舗は削除ガードが明示的に拒むため、来店の店舗が欠けることはない。
  // キャストだけは指名も割り当ても無い受注があるので外部結合にする。
  String MEMBER_VISIT_SELECT =
      """
      select a.id as id, a.createdAt as createdAt, o.id as orderId,
             o.businessDate as visitedOn, st.name as storeName,
             o.pax as pax, k.name as castName
      from com.kizuna.order.domain.OrderAttribution a
        join com.kizuna.order.domain.Order o on o.id = a.orderId
        join com.kizuna.store.domain.Store st on st.id = o.storeId
        left join com.kizuna.cast.domain.Cast k on k.id = o.castId
      """;

  // 本人は店舗文脈を確立できず storeFilter は働かないため、帰属先会員の一致が唯一の隔離境界である。受注の状態は
  // 条件に入れない — 来店として見えるかは帰属記録の有無と有効性だけで決まる（ADR 0009）。完了後に取り消された
  // 受注も来店として残り、台帳側が取消仕訳で清算する。訂正は人手の無効化だけが行う。
  String MEMBER_VISIT_WHERE =
      " where a.memberId = :memberId and a.status = com.kizuna.order.domain.OrderAttributionStatus.ACTIVE ";

  // 新しい帰属から。カーソルの比較（下記 AFTER 条件）はこの並びと同じ列の組で行う。
  String MEMBER_VISIT_ORDER = " order by a.createdAt desc, a.id desc";

  /**
   * 会員本人の来店履歴（跨店集約）の先頭。
   *
   * <p>並びは帰属の作成時刻の降順に一意な副キー id を重ねて全順序にする（カーソルが 1 行を一意に指せるため）。
   */
  @Query(MEMBER_VISIT_SELECT + MEMBER_VISIT_WHERE + MEMBER_VISIT_ORDER)
  List<MemberVisitView> findMemberVisitViews(@Param("memberId") Long memberId, Limit limit);

  /** 会員本人の来店履歴の続き。渡された位置より後ろ（＝より古い側）だけを返す。 */
  @Query(
      MEMBER_VISIT_SELECT
          + MEMBER_VISIT_WHERE
          + """
            and (a.createdAt < :cursorCreatedAt
                 or (a.createdAt = :cursorCreatedAt and a.id < :cursorId))
            """
          + MEMBER_VISIT_ORDER)
  List<MemberVisitView> findMemberVisitViewsAfter(
      @Param("memberId") Long memberId,
      @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      Limit limit);
}
