package com.kizuna.order.domain;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository
    extends JpaRepository<Order, String>, JpaSpecificationExecutor<Order> {

  /** 指定店舗に指定状態の受注が 1 件でも存在するか。 */
  @Query(
      "select count(o) > 0 from com.kizuna.order.domain.Order o"
          + " where o.storeId = :storeId and o.status = :status")
  boolean existsByStoreIdAndStatus(
      @Param("storeId") Long storeId, @Param("status") OrderStatus status);

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
             o.contactName as contactName, o.contactPhoneNumber as contactPhoneNumber,
             o.castId as castId, k.name as castName,
             o.pax as pax,
             o.courseMinutes as courseMinutes, o.extensionMinutes as extensionMinutes,
             o.optionCodes as optionCodes, o.discountName as discountName,
             o.manualDiscount as manualDiscount, o.carrier as carrier,
             o.mediaName as mediaName, o.totalFee as totalFee,
             o.usedPoints as usedPoints, o.autoGrantPoints as autoGrantPoints,
             o.remarks as remarks,
             o.castDriverMessage as castDriverMessage, o.status as status,
             o.receptionRoute as receptionRoute,
             o.requesterMemberCode as requesterMemberCode,
             o.requesterDeclaredName as requesterDeclaredName,
             o.locationAddress as locationAddress, o.locationBuilding as locationBuilding,
             o.cancelledReason as cancelledReason, cu.displayName as cancelledByName,
             o.cancelledAt as cancelledAt,
             o.createdAt as createdAt
      from com.kizuna.order.domain.Order o
        left join com.kizuna.customer.domain.Customer c on c.id = o.customerId
        left join com.kizuna.cast.domain.Cast k on k.id = o.castId
        left join com.kizuna.user.domain.PlatformUser u on u.id = o.receptionistId
        left join com.kizuna.user.domain.PlatformUser cu on cu.id = o.cancelledBy
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

  /**
   * 名指された受注の読み側 projection をまとめて引く。
   *
   * <p>並びは<b>返さない</b> — 検索・並び替えの条件は {@code OrderSearchQuery} が動的に組み立てて ID の並びを確定させており、 ここで別の並びを掛けると
   * 2 箇所が並びを主張して食い違う。呼出側が ID の並びどおりに並べ直す。
   */
  @Query(VIEW_SELECT + " where o.id in :ids")
  List<OrderView> findViewsByIds(@Param("ids") Collection<String> ids);

  /**
   * 現店舗の受注 1 件を集約として引く。
   *
   * <p>JPQL で書くのは {@code storeFilter} を効かせるため。Order は StoreScopedEntity だが、Hibernate のフィルタは {@code
   * EntityManager.find} には掛からず、派生の {@code findById} はそこへ落ちる。複数店舗を授権された操作者は現店舗の文脈のまま 他店舗の受注 ID
   * を指せるので、店舗を跨がない読み書きはこの形で引く。
   *
   * <p>受注に紐づく帰属記録・伝票トークンは platform 帰属で店舗行分離機構に載らない。それらを受注 ID で引く操作は、 先にこの読み口で店舗の所有を確かめてから行う。
   */
  @Query("select o from com.kizuna.order.domain.Order o where o.id = :id")
  Optional<Order> findScopedById(@Param("id") String id);

  /**
   * 現店舗の受注 1 件を、その受注に紐づく行を書き換える間だけ押さえて引く。
   *
   * <p>伝票トークンの再発行は、先行するトークンを失効させてから 1 本発行する。押さえるのが受注なのは、まだ 1 本も発行されて
   * いない受注（完了時に会員へ帰属した受注）には押さえられるトークン行が無く、並行する 2 つの再発行が両方とも発行して
   * しまうため。受注は帰属記録とトークンの共通の親であり、この操作の直列化の単位になる。
   *
   * <p>再発行はこの後さらにトークン行を押さえる（{@code OrderReceiptTokenRepository#findByOrderIdForUpdate}）。申領 （{@code
   * MemberReceiptClaimService}）が押さえるのはトークン行だけで受注行を要求しないため、受注 → トークンの 一方向しか無く待ちが環にならない。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from com.kizuna.order.domain.Order o where o.id = :id")
  Optional<Order> findScopedByIdForUpdate(@Param("id") String id);

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

  // 本人は店舗文脈を確立できず storeFilter は働かないため、申請者の一致が唯一の隔離境界である。先頭と続きの
  // 問い合わせが同じ条件を共有する。
  String MEMBER_VIEW_WHERE = " where o.requesterMemberId = :memberId ";

  // 新しい業務日から。カーソルの比較（下記 AFTER 条件）はこの並びと同じ列の組で行う。
  String MEMBER_VIEW_ORDER = " order by o.businessDate desc, o.id desc";

  /**
   * 会員本人の予約一覧（跨店集約）の先頭。
   *
   * <p>並びは業務日の降順に一意な副キー id を重ねて全順序にする（カーソルが 1 行を一意に指せるため）。
   */
  @Query(MEMBER_VIEW_SELECT + MEMBER_VIEW_WHERE + MEMBER_VIEW_ORDER)
  List<MemberOrderView> findMemberViews(@Param("memberId") Long memberId, Limit limit);

  /** 会員本人の予約一覧の続き。渡された位置より後ろだけを返す。 */
  @Query(
      MEMBER_VIEW_SELECT
          + MEMBER_VIEW_WHERE
          + """
            and (o.businessDate < :cursorBusinessDate
                 or (o.businessDate = :cursorBusinessDate and o.id < :cursorId))
            """
          + MEMBER_VIEW_ORDER)
  List<MemberOrderView> findMemberViewsAfter(
      @Param("memberId") Long memberId,
      @Param("cursorBusinessDate") LocalDate cursorBusinessDate,
      @Param("cursorId") String cursorId,
      Limit limit);

  /** 会員本人の予約 1 件。一覧と同じく申請者の一致を問い合わせに載せ、他人の予約には到達させない。 */
  @Query(MEMBER_VIEW_SELECT + " where o.requesterMemberId = :memberId and o.id = :orderId")
  Optional<MemberOrderView> findMemberView(
      @Param("memberId") Long memberId, @Param("orderId") String orderId);
}
