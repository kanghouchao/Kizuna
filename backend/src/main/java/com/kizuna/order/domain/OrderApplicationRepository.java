package com.kizuna.order.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface OrderApplicationRepository extends StoreScopedRepository<OrderApplication> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from OrderApplication a where a.id = :id and a.storeId = :storeId")
  Optional<OrderApplication> findByIdAndStoreIdForUpdate(
      @Param("id") String id, @Param("storeId") Long storeId);

  /** 店舗を跨ぐ照会。境界は申請者の会員 id（requester_member_id）。 */
  Optional<OrderApplication> findByIdAndRequesterMemberId(String id, Long requesterMemberId);

  /**
   * 店舗を跨ぐ照会。境界は申請者の会員 id — 会員退会が全店舗の PENDING 申請を取り下げる入口。
   *
   * <p>待たずに押さえる（{@code FOR UPDATE NOWAIT}）。退会は会員行を先に握っており、確定処理は申請行 → 関連の挿入（会員行へ KEY
   * SHARE）の順なので、申請行を待つと互いに待ち合って死錠する。取れなければ呼出側が競合として撥ね、利用者がやり直す。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
  @Query(
      "select a from OrderApplication a where a.requesterMemberId = :memberId"
          + " and a.status = com.kizuna.order.domain.OrderApplicationStatus.PENDING order by a.id")
  List<OrderApplication> lockPendingByRequesterMemberIdWithoutWaiting(
      @Param("memberId") Long memberId);

  // キャストの表示名は ID 参照のため JPQL join で取得する。
  String VIEW_SELECT =
      """
      select a.id as id,
             a.businessDate as businessDate,
             a.arrivalScheduledStartTime as arrivalScheduledStartTime,
             a.pax as pax,
             a.castId as castId, k.name as castName,
             a.remarks as remarks, a.status as status,
             a.requesterMemberCode as requesterMemberCode,
             a.requesterDeclaredName as requesterDeclaredName,
             a.contactName as contactName, a.contactPhoneNumber as contactPhoneNumber,
             a.contactEmail as contactEmail, a.contactLineId as contactLineId,
             a.orderId as orderId, a.declinedReason as declinedReason
      from com.kizuna.order.domain.OrderApplication a
        left join com.kizuna.cast.domain.CastEnrollment ce
          on ce.id = a.castId and ce.storeId = a.storeId
        left join com.kizuna.cast.domain.CastProfile k
          on k.enrollmentId = ce.id and k.storeId = a.storeId
      """;

  @Query(VIEW_SELECT + " where a.id = :id and a.storeId = :storeId")
  Optional<OrderApplicationView> findView(@Param("storeId") Long storeId, @Param("id") String id);

  String VIEW_WHERE = " where a.storeId = :storeId and a.status in :statuses ";

  /** 店舗の申請一覧。状態の群を指定して辿る（受付箱は PENDING）。並びは呼出側の Pageable が決める。 */
  @Query(
      value = VIEW_SELECT + VIEW_WHERE,
      countQuery = "select count(a) from com.kizuna.order.domain.OrderApplication a" + VIEW_WHERE)
  Page<OrderApplicationView> findViews(
      @Param("storeId") Long storeId,
      @Param("statuses") Collection<OrderApplicationStatus> statuses,
      Pageable pageable);

  String MEMBER_VIEW_SELECT =
      """
      select a.id as id, a.storeId as storeId, st.name as storeName,
             a.businessDate as businessDate,
             a.arrivalScheduledStartTime as arrivalScheduledStartTime,
             a.pax as pax, k.name as castName, a.status as status
      from com.kizuna.order.domain.OrderApplication a
        join com.kizuna.store.domain.Store st on st.id = a.storeId
        left join com.kizuna.cast.domain.CastEnrollment ce
          on ce.id = a.castId and ce.storeId = a.storeId
        left join com.kizuna.cast.domain.CastProfile k
          on k.enrollmentId = ce.id and k.storeId = a.storeId
      """;

  // 本人の申請を店舗を跨いで返すため、申請者の一致が唯一の隔離境界である。一覧と件数の
  // 問い合わせが同じ条件を共有する。
  String MEMBER_VIEW_WHERE = " where a.requesterMemberId = :memberId ";

  /** 会員本人の予約申請一覧（跨店集約）。並びは呼出側の Pageable が決める。 */
  @Query(
      value = MEMBER_VIEW_SELECT + MEMBER_VIEW_WHERE,
      countQuery =
          """
          select count(a) from com.kizuna.order.domain.OrderApplication a
            join com.kizuna.store.domain.Store st on st.id = a.storeId
          """
              + MEMBER_VIEW_WHERE)
  Page<MemberOrderApplicationView> findMemberViews(
      @Param("memberId") Long memberId, Pageable pageable);

  /** 一覧と同じく申請者の一致を問い合わせに載せ、他人の申請には到達させない。 */
  @Query(MEMBER_VIEW_SELECT + " where a.requesterMemberId = :memberId and a.id = :applicationId")
  Optional<MemberOrderApplicationView> findMemberView(
      @Param("memberId") Long memberId, @Param("applicationId") String applicationId);
}
