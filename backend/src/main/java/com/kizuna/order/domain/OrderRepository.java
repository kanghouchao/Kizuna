package com.kizuna.order.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
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
             o.mediaName as mediaName, o.totalFee as totalFee,
             o.usedPoints as usedPoints, o.autoGrantPoints as autoGrantPoints,
             o.remarks as remarks,
             o.castDriverMessage as castDriverMessage, o.status as status,
             o.receptionRoute as receptionRoute,
             o.requesterMemberCode as requesterMemberCode,
             o.locationAddress as locationAddress, o.locationBuilding as locationBuilding,
             o.createdAt as createdAt
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

  // 未確定の申請の抽出条件。先頭と続きの問い合わせが同じ条件を共有する（片方だけ直すと、続きの取得が
  // 先頭の取得と違う母集合を辿り、到達できない申請を生む）。
  String PENDING_REQUEST_WHERE =
      """
      where o.status = com.kizuna.order.domain.OrderStatus.CREATED
        and o.receptionRoute = com.kizuna.order.domain.ReceptionRoute.WEB
        and o.requesterMemberCode is not null
      """;

  // 古い順。カーソルの比較（下記 AFTER 条件）はこの並びと同じ列の組で行う。
  String PENDING_REQUEST_ORDER = " order by o.createdAt asc, o.id asc";

  /**
   * 予約受付 inbox が扱う未確定の申請（会員ポータルからの Web 申請のうち、まだ確定も謝絶もしていないもの）の先頭。
   *
   * <p>申請の判定は受付経路だけでは足りない — 受付経路は店舗が手入力の受注にも自由に付けられる記録項目であり、 申請であることの証拠にはならない。申請者まで見て初めて会員の申請だと言える。
   *
   * <p>申請者の判定に使うのは会員 ID ではなく会員コードのスナップショットである。会員行が消えると FK は SET NULL で 会員 ID
   * が欠落するが、未確定の申請はそれでも店舗が処理し終える必要がある。ID を要求すると、その申請が CREATED のまま inbox から消えて処理不能になる。
   *
   * <p>処理済みの閲覧は受注一覧の責務なので、ここでは未確定のものだけを古い順に返す。並びは問い合わせ側で 一意な副キー id まで固定する（カーソルが 1 行を一意に指せる全順序が要る）。
   */
  @Query(VIEW_SELECT + PENDING_REQUEST_WHERE + PENDING_REQUEST_ORDER)
  List<OrderView> findPendingReservationRequestViews(Limit limit);

  /**
   * 未確定の申請の続き。渡された位置より後ろだけを返す。
   *
   * <p>位置は「何件目か」ではなく並びの鍵そのものなので、確定・謝絶で手前の行が消えても後続は繰り上がらない — offset
   * ページングと違い、処理の直後に続きを取ってもページ境界の申請を飛ばさない。
   */
  @Query(
      VIEW_SELECT
          + PENDING_REQUEST_WHERE
          + """
            and (o.createdAt > :cursorCreatedAt
                 or (o.createdAt = :cursorCreatedAt and o.id > :cursorId))
            """
          + PENDING_REQUEST_ORDER)
  List<OrderView> findPendingReservationRequestViewsAfter(
      @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
      @Param("cursorId") String cursorId,
      Limit limit);

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
