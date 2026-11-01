package com.kizuna.order.domain;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderAttributionRepository extends JpaRepository<OrderAttribution, Long> {

  // 帰属記録は店舗列を持たないグループ帰属なので、店舗の条件は受注への join に置く。
  String FOR_ORDER =
      """
      select a from com.kizuna.order.domain.OrderAttribution a
        join com.kizuna.order.domain.Order o on o.id = a.orderId
      where o.storeId = :storeId and a.orderId = :orderId
      order by a.id desc
      """;

  /** 指定店舗の受注の帰属記録を新しい順に。無効化で行を消さないため 1 受注が複数行を持ちうるが、有効な行は部分一意索引により高々 1 件。 */
  @Query(FOR_ORDER)
  List<OrderAttribution> findByStoreIdAndOrderIdOrderByIdDesc(
      @Param("storeId") Long storeId, @Param("orderId") String orderId);

  /**
   * 指定店舗の受注の帰属記録を新しい順に、{@code limit} 件まで。先頭は有効な行があれば必ずそれになる（無効化は過去の行にしか起こらないため）。
   *
   * <p>現況だけが要る経路は履歴を丸ごと読まずに 1 件で引く。全件は「1 件でもあるか」と「有効な行があるか」を同時に判じる再発行の門で要る。
   */
  @Query(FOR_ORDER)
  List<OrderAttribution> findByStoreIdAndOrderIdOrderByIdDesc(
      @Param("storeId") Long storeId, @Param("orderId") String orderId, Limit limit);

  /** 店舗を跨ぐ会員照会。境界は帰属先の会員 id。有効に帰属した完了受注の件数で、会員ランクの昇格指標の一方。無効化された帰属は数えない。 */
  long countByMemberIdAndStatus(Long memberId, OrderAttributionStatus status);

  // 会員本人の来店一覧。帰属記録が正本で、表示に要る属性は受注へ join して導出する（ADR 0003 と同じ扱い。
  // 帰属記録は台帳と同じく グループ帰属なので同 ADR が名指す店舗作用域エンティティそのものではない）。店舗は
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
        left join com.kizuna.cast.domain.CastEnrollment ce
          on ce.id = o.castId and ce.storeId = o.storeId
        left join com.kizuna.cast.domain.CastProfile k
          on k.enrollmentId = ce.id and k.storeId = o.storeId
      """;

  // 本人の来店を店舗を跨いで返すため、帰属先会員の一致が唯一の隔離境界である。受注の状態は
  // 条件に入れない — 来店として見えるかは帰属記録の有無と有効性だけで決まる（ADR 0009）。完了後に取り消された
  // 受注も来店として残り、台帳側が取消仕訳で収束させる。訂正は人手の無効化だけが行う。
  String MEMBER_VISIT_WHERE =
      " where a.memberId = :memberId and a.status = com.kizuna.order.domain.OrderAttributionStatus.ACTIVE ";

  /** 来店履歴（跨店集約）。並びは呼出側の Pageable が決める。件数は一覧と同じ内部結合で数える。 */
  @Query(
      value = MEMBER_VISIT_SELECT + MEMBER_VISIT_WHERE,
      countQuery =
          """
          select count(a) from com.kizuna.order.domain.OrderAttribution a
            join com.kizuna.order.domain.Order o on o.id = a.orderId
            join com.kizuna.store.domain.Store st on st.id = o.storeId
          """
              + MEMBER_VISIT_WHERE)
  Page<MemberVisitView> findMemberVisitViews(@Param("memberId") Long memberId, Pageable pageable);
}
