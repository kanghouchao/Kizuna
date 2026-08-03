package com.kizuna.order.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository
    extends JpaRepository<Order, String>, JpaSpecificationExecutor<Order> {

  // 関連集約の表示名は ID 参照のため JPQL join で取得する。
  // Order / Cast は HQL の予約語と衝突しうるため FQCN でエンティティを参照する。
  String VIEW_SELECT =
      """
      select o.id as id,
             o.receptionistId as receptionistId, u.displayName as receptionistName,
             o.businessDate as businessDate,
             o.arrivalScheduledStartTime as arrivalScheduledStartTime,
             o.arrivalScheduledEndTime as arrivalScheduledEndTime,
             o.customerId as customerId, c.name as customerName,
             o.castId as castId, k.name as castName,
             o.pax as pax,
             o.courseMinutes as courseMinutes, o.extensionMinutes as extensionMinutes,
             o.optionCodes as optionCodes, o.discountName as discountName,
             o.manualDiscount as manualDiscount, o.carrier as carrier,
             o.mediaName as mediaName, o.usedPoints as usedPoints,
             o.manualGrantPoints as manualGrantPoints, o.remarks as remarks,
             o.castDriverMessage as castDriverMessage, o.status as status,
             o.receptionRoute as receptionRoute,
             o.requesterMemberCode as requesterMemberCode,
             o.locationAddress as locationAddress, o.locationBuilding as locationBuilding
      from com.kizuna.order.domain.Order o
        left join com.kizuna.customer.domain.Customer c on c.id = o.customerId
        left join com.kizuna.cast.domain.Cast k on k.id = o.castId
        left join com.kizuna.user.domain.PlatformUser u on u.id = o.receptionistId
      """;

  @Query(
      value = VIEW_SELECT + " where (:customerId is null or o.customerId = :customerId)",
      countQuery =
          """
          select count(o) from com.kizuna.order.domain.Order o
          where (:customerId is null or o.customerId = :customerId)
          """)
  Page<OrderView> findAllViews(@Param("customerId") String customerId, Pageable pageable);

  @Query(VIEW_SELECT + " where o.id = :id")
  Optional<OrderView> findViewById(@Param("id") String id);

  // 平台横断一覧（集合作用域）。where 句を書かず、濾過は storeSetFilter が session 層で行う。
  // 店舗（store）表示名の join は張らない。
  String PLATFORM_VIEW_SELECT =
      """
      select o.id as id, o.storeId as storeId,
             o.businessDate as businessDate,
             o.arrivalScheduledStartTime as arrivalScheduledStartTime,
             o.arrivalScheduledEndTime as arrivalScheduledEndTime,
             o.status as status
      from com.kizuna.order.domain.Order o
      """;

  @Query(
      value = PLATFORM_VIEW_SELECT,
      countQuery = "select count(o) from com.kizuna.order.domain.Order o")
  Page<PlatformOrderView> findPlatformViews(Pageable pageable);

  /**
   * 会員本人の予約一覧（跨店集約）。
   *
   * <p>本人は店舗文脈を確立できず storeFilter は働かないため、where 句の {@code requesterMemberId} が唯一の隔離境界である。並びは業務日の降順に
   * 一意な副キー id を重ねて全順序にする（offset ページングで行の重複・欠落を起こさないため）。
   */
  String MEMBER_VIEW_SELECT =
      """
      select o.id as id, o.storeId as storeId, st.name as storeName,
             o.businessDate as businessDate,
             o.arrivalScheduledStartTime as arrivalScheduledStartTime,
             o.pax as pax, k.name as castName, o.status as status
      from com.kizuna.order.domain.Order o
        join com.kizuna.store.domain.Store st on st.id = o.storeId
        left join com.kizuna.cast.domain.Cast k on k.id = o.castId
      """;

  @Query(
      value =
          MEMBER_VIEW_SELECT
              + """
              where o.requesterMemberId = :memberId
              order by o.businessDate desc, o.id desc
              """,
      countQuery =
          """
          select count(o) from com.kizuna.order.domain.Order o
          where o.requesterMemberId = :memberId
          """)
  Page<MemberOrderView> findMemberViews(@Param("memberId") Long memberId, Pageable pageable);

  /** 会員本人の予約 1 件。一覧と同じく申請者の一致を問い合わせに載せ、他人の予約には到達させない。 */
  @Query(MEMBER_VIEW_SELECT + " where o.requesterMemberId = :memberId and o.id = :orderId")
  Optional<MemberOrderView> findMemberView(
      @Param("memberId") Long memberId, @Param("orderId") String orderId);
}
