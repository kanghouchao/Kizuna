package com.kizuna.order.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends StoreScopedRepository<Order> {

  String SELF_FROM =
      """
      from com.kizuna.order.domain.Order o
      join com.kizuna.cast.domain.CastEnrollment e on e.id = o.castId and e.storeId = o.storeId
      join com.kizuna.cast.domain.Cast c on c.id = e.castId
      join com.kizuna.store.domain.Store st on st.id = o.storeId
      where c.userId = :userId
      """;
  String SELF_SELECT =
      """
      select o.id as orderId, o.castId as enrollmentId, o.storeId as storeId,
             st.name as storeName, o.businessDate as businessDate, o.completedAt as completedAt,
             o.status as status, o.completionInvalidated as completionInvalidated,
             o.accruedRemuneration as accruedRemuneration,
             (select cast(coalesce(sum(l.remuneration), 0) as Integer)
              from com.kizuna.order.domain.OrderFeeLine l
              where l.orderId = o.id and l.storeId = o.storeId) as agreedRemuneration
      """;
  String SELF_SORT = " order by o.businessDate desc, o.id desc";

  /** 店舗を跨ぐ本人照会。境界は担当在籍の本人の user_id。 */
  @Query(value = SELF_SELECT + SELF_FROM + SELF_SORT, countQuery = "select count(o) " + SELF_FROM)
  Page<SelfRemunerationView> findSelfRemunerations(Long userId, Pageable pageable);

  /** 店舗を跨ぐ本人照会。境界は担当在籍の本人の user_id。 */
  @Query(
      value = SELF_SELECT + SELF_FROM + " and e.id = :enrollmentId" + SELF_SORT,
      countQuery = "select count(o) " + SELF_FROM + " and e.id = :enrollmentId")
  Page<SelfRemunerationView> findSelfRemunerationsByEnrollment(
      Long userId, String enrollmentId, Pageable pageable);

  /** 店舗を跨ぐ本人照会。境界は担当在籍の本人の user_id。 */
  @Query(SELF_SELECT + SELF_FROM + " and o.id = :id")
  Optional<SelfRemunerationView> findSelfRemuneration(Long userId, String id);

  /** 店舗を跨ぐ本人照会。境界は担当在籍の本人の user_id。 */
  @Query("select o " + SELF_FROM + " and o.id = :id")
  Optional<Order> findSelfOrder(Long userId, String id);

  /** 店舗を跨ぐ照会。境界は伝票トークンのダイジェストで、そのトークンが指す受注だけを返す。 */
  @Query(
      """
      select o from com.kizuna.order.domain.Order o
      join com.kizuna.order.domain.OrderReceiptToken t on t.orderId = o.id
      where t.tokenDigest = :tokenDigest
      """)
  Optional<Order> findByReceiptTokenDigest(@Param("tokenDigest") String tokenDigest);

  /** 店舗を跨ぐ参照照会。境界は呼び手が店舗条件で引いた在籍 id（cast_id）集合。 */
  @Query(
      """
      select o.castId from com.kizuna.order.domain.Order o where o.castId in :castIds
      union
      select a.castId from com.kizuna.order.domain.OrderApplication a where a.castId in :castIds
      """)
  Set<String> findReferencedCastIds(@Param("castIds") Collection<String> castIds);

  /** 指定店舗に指定状態の受注が 1 件でも存在するか。 */
  @Query(
      "select count(o) > 0 from com.kizuna.order.domain.Order o"
          + " where o.storeId = :storeId and o.status = :status")
  boolean existsByStoreIdAndStatus(
      @Param("storeId") Long storeId, @Param("status") OrderStatus status);

  // 関連集約の表示名は ID 参照のため JPQL join で取得する。
  // Order は HQL の予約語と衝突しうるため FQCN でエンティティを参照する。
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select o from com.kizuna.order.domain.Order o where o.storeId = :storeId and o.castId = :enrollment"
          + " and o.status in (com.kizuna.order.domain.OrderStatus.CONFIRMED, com.kizuna.order.domain.OrderStatus.IN_SERVICE)"
          + " and exists (select s.id from OrderSpecialService s where s.orderId = o.id and s.storeId = o.storeId and s.serviceId = :serviceId) order by o.id")
  List<Order> findUnfinishedSpecialOrders(Long storeId, String enrollment, String serviceId);

  String VIEW_SELECT =
      """
      select o.completionInvalidated as completionInvalidated, o.replacementForOrderId as replacementForOrderId,
             o.startedAt as startedAt,
             o.completedAt as completedAt, o.accruedRemuneration as accruedRemuneration,
             case when o.completionInvalidated then 0 else (select cast(coalesce(sum(r.remuneration), 0) as Integer) from com.kizuna.order.domain.OrderFeeLine r where r.orderId = o.id and r.storeId = o.storeId) end as totalRemuneration,
             (select cast(count(distinct e.serviceId) as Integer) from OrderSpecialServiceEvent e
              where e.orderId = o.id and e.storeId = o.storeId and e.kind = 'REJECTED'
              and o.status in (com.kizuna.order.domain.OrderStatus.CONFIRMED, com.kizuna.order.domain.OrderStatus.IN_SERVICE)
              and not exists (select r.id from OrderSpecialServiceEvent r where r.rejectionEventId = e.id and r.storeId = e.storeId)) as unresolvedSpecialServiceCount,
             o.id as id,
             o.receptionistId as receptionistId, u.displayName as receptionistName,
             o.businessDate as businessDate,
             o.arrivalScheduledStartTime as arrivalScheduledStartTime,
             o.arrivalScheduledEndTime as arrivalScheduledEndTime,
             o.customerId as customerId, c.name as customerName,
             o.contactName as contactName, o.contactPhoneNumber as contactPhoneNumber,
             o.contactEmail as contactEmail, o.contactLineId as contactLineId,
             o.castId as castId, k.name as castName,
             o.pax as pax,
             o.course as course,
             o.extensionMinutes as extensionMinutes,
             o.actualArrivalTime as actualArrivalTime, o.actualEndTime as actualEndTime,
             o.carrier as carrier,
             o.mediaName as mediaName, o.totalFee as totalFee,
             (select cast(coalesce(sum(-fl.amount), 0) as Integer)
                from com.kizuna.order.domain.OrderFeeLine fl
               where fl.orderId = o.id and fl.storeId = o.storeId
                 and fl.kind = com.kizuna.order.domain.OrderFeeLineKind.POINT_REDEMPTION)
               as usedPoints,
             o.autoGrantPoints as autoGrantPoints,
             o.remarks as remarks,
             o.castDriverMessage as castDriverMessage, o.status as status,
             o.receptionRoute as receptionRoute,
             o.requesterMemberCode as requesterMemberCode,
             o.requesterDeclaredName as requesterDeclaredName,
             o.locationAddress as locationAddress, o.locationBuilding as locationBuilding,
             o.cancelledReason as cancelledReason, cu.displayName as cancelledByName,
             o.cancelledAt as cancelledAt,
             o.createdAt as createdAt, o.version as version
      from com.kizuna.order.domain.Order o
        left join com.kizuna.customer.domain.Customer c
          on c.id = o.customerId and c.storeId = o.storeId
        left join com.kizuna.cast.domain.CastEnrollment ce
          on ce.id = o.castId and ce.storeId = o.storeId
        left join com.kizuna.cast.domain.CastProfile k
          on k.enrollmentId = ce.id and k.storeId = o.storeId
        left join com.kizuna.user.domain.User u on u.id = o.receptionistId
        left join com.kizuna.user.domain.User cu on cu.id = o.cancelledBy
      """;

  @Query(
      value =
          VIEW_SELECT
              + " where o.storeId = :storeId and (:customerId is null or o.customerId = :customerId)",
      countQuery =
          """
          select count(o) from com.kizuna.order.domain.Order o
          where o.storeId = :storeId and (:customerId is null or o.customerId = :customerId)
          """)
  Page<OrderView> findAllViews(
      @Param("storeId") Long storeId, @Param("customerId") String customerId, Pageable pageable);

  @Query(VIEW_SELECT + " where o.id = :id and o.storeId = :storeId")
  Optional<OrderView> findViewById(@Param("storeId") Long storeId, @Param("id") String id);

  /**
   * 名指された受注の読み側 projection をまとめて引く。
   *
   * <p>並びは<b>返さない</b> — 検索・並び替えの条件は {@code OrderSearchQuery} が動的に組み立てて ID の並びを確定させており、 ここで別の並びを掛けると
   * 2 箇所が並びを主張して食い違う。呼出側が ID の並びどおりに並べ直す。
   */
  @Query(VIEW_SELECT + " where o.id in :ids and o.storeId = :storeId")
  List<OrderView> findViewsByIds(
      @Param("storeId") Long storeId, @Param("ids") Collection<String> ids);

  /**
   * 指定店舗の受注 1 件を集約として引く。
   *
   * <p>受注に紐づく帰属記録・伝票トークンは グループ帰属で店舗条件を持たない。それらを受注 ID で引く操作は、 先にこの読み口で店舗の所有を確かめてから行う。
   */
  @Override
  Optional<Order> findByIdAndStoreId(String id, Long storeId);

  /**
   * 指定店舗の受注 1 件を、その受注に紐づく行を書き換える間だけ押さえて引く。
   *
   * <p>伝票トークンの再発行は、先行するトークンを失効させてから 1 本発行する。押さえるのが受注なのは、まだ 1 本も発行されて
   * いない受注（完了時に会員へ帰属した受注）には押さえられるトークン行が無く、並行する 2 つの再発行が両方とも発行して
   * しまうため。受注は帰属記録とトークンの共通の親であり、この操作の直列化の単位になる。
   *
   * <p>再発行はこの後さらにトークン行を押さえる（{@code OrderReceiptTokenRepository#findByOrderIdForUpdate}）。申領 （{@code
   * MemberReceiptClaimService}）が押さえるのはトークン行だけで受注行を要求しないため、受注 → トークンの 一方向しか無く待ちが環にならない。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from com.kizuna.order.domain.Order o where o.id = :id and o.storeId = :storeId")
  Optional<Order> findByIdAndStoreIdForUpdate(
      @Param("id") String id, @Param("storeId") Long storeId);

  // 店舗集合の横断一覧。店舗（store）表示名の join は張らない。
  String GROUP_WIDE_VIEW_SELECT =
      """
      select o.completionInvalidated as completionInvalidated, o.replacementForOrderId as replacementForOrderId,
             o.startedAt as startedAt,
             o.completedAt as completedAt, o.accruedRemuneration as accruedRemuneration,
             case when o.completionInvalidated then 0 else (select cast(coalesce(sum(r.remuneration), 0) as Integer) from com.kizuna.order.domain.OrderFeeLine r where r.orderId = o.id and r.storeId = o.storeId) end as totalRemuneration,
             (select cast(count(distinct e.serviceId) as Integer) from OrderSpecialServiceEvent e
              where e.orderId = o.id and e.storeId = o.storeId and e.kind = 'REJECTED'
              and o.status in (com.kizuna.order.domain.OrderStatus.CONFIRMED, com.kizuna.order.domain.OrderStatus.IN_SERVICE)
              and not exists (select r.id from OrderSpecialServiceEvent r where r.rejectionEventId = e.id and r.storeId = e.storeId)) as unresolvedSpecialServiceCount,
             o.id as id, o.storeId as storeId, o.course as course, o.totalFee as totalFee,
             o.businessDate as businessDate,
             o.arrivalScheduledStartTime as arrivalScheduledStartTime,
             o.arrivalScheduledEndTime as arrivalScheduledEndTime,
             o.status as status
      from com.kizuna.order.domain.Order o
      """;

  /** 店舗を跨ぐ照会。店舗の境界は無く、全店舗（ALL_STORES）を授権された呼び手だけが使う。 */
  @Query(
      value = GROUP_WIDE_VIEW_SELECT,
      countQuery = "select count(o) from com.kizuna.order.domain.Order o")
  Page<AdminOrderView> findGroupWideViews(Pageable pageable);

  /** 店舗を跨ぐ照会。境界は呼び手の授権店舗集合（SPECIFIC_STORES）。 */
  @Query(
      value = GROUP_WIDE_VIEW_SELECT + " where o.storeId in :storeIds",
      countQuery =
          "select count(o) from com.kizuna.order.domain.Order o where o.storeId in :storeIds")
  Page<AdminOrderView> findGroupWideViewsInStores(
      @Param("storeIds") Collection<Long> storeIds, Pageable pageable);
}
