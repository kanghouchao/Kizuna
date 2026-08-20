package com.kizuna.order.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderApplicationRepository extends JpaRepository<OrderApplication, String> {

  // キャストの表示名は ID 参照のため JPQL join で取得する。Cast は HQL の予約語と衝突しうるため FQCN で参照する。
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
             a.orderId as orderId, a.declinedReason as declinedReason
      from com.kizuna.order.domain.OrderApplication a
        left join com.kizuna.cast.domain.Cast k on k.id = a.castId
      """;

  String VIEW_WHERE = " where a.status in :statuses ";

  // 受付箱は希望日の早い順（近い来店から処理する）。一意な副キー id を重ねて全順序にし、カーソルの比較
  // （下記 AFTER 条件）はこの並びと同じ列の組で行う。
  String VIEW_ORDER = " order by a.businessDate asc, a.id asc";

  /** 店舗の申請一覧の先頭。状態の群を指定して辿る（受付箱は PENDING）。 */
  @Query(VIEW_SELECT + VIEW_WHERE + VIEW_ORDER)
  List<OrderApplicationView> findViews(
      @Param("statuses") Collection<OrderApplicationStatus> statuses, Limit limit);

  /** 店舗の申請一覧の続き。渡された位置より後ろだけを返す。 */
  @Query(
      VIEW_SELECT
          + VIEW_WHERE
          + """
            and (a.businessDate > :cursorBusinessDate
                 or (a.businessDate = :cursorBusinessDate and a.id > :cursorId))
            """
          + VIEW_ORDER)
  List<OrderApplicationView> findViewsAfter(
      @Param("statuses") Collection<OrderApplicationStatus> statuses,
      @Param("cursorBusinessDate") LocalDate cursorBusinessDate,
      @Param("cursorId") String cursorId,
      Limit limit);

  String MEMBER_VIEW_SELECT =
      """
      select a.id as id, a.storeId as storeId, st.name as storeName,
             a.businessDate as businessDate,
             a.arrivalScheduledStartTime as arrivalScheduledStartTime,
             a.pax as pax, k.name as castName, a.status as status
      from com.kizuna.order.domain.OrderApplication a
        join com.kizuna.store.domain.Store st on st.id = a.storeId
        left join com.kizuna.cast.domain.Cast k on k.id = a.castId
      """;

  // 本人は店舗文脈を確立できず storeFilter は働かないため、申請者の一致が唯一の隔離境界である。先頭と続きの
  // 問い合わせが同じ条件を共有する。
  String MEMBER_VIEW_WHERE = " where a.requesterMemberId = :memberId ";

  // 新しい希望日から。カーソルの比較（下記 AFTER 条件）はこの並びと同じ列の組で行う。
  String MEMBER_VIEW_ORDER = " order by a.businessDate desc, a.id desc";

  /**
   * 会員本人の予約申請一覧（跨店集約）の先頭。
   *
   * <p>並びは希望日の降順に一意な副キー id を重ねて全順序にする（カーソルが 1 行を一意に指せるため）。
   */
  @Query(MEMBER_VIEW_SELECT + MEMBER_VIEW_WHERE + MEMBER_VIEW_ORDER)
  List<MemberOrderApplicationView> findMemberViews(@Param("memberId") Long memberId, Limit limit);

  /** 会員本人の予約申請一覧の続き。渡された位置より後ろだけを返す。 */
  @Query(
      MEMBER_VIEW_SELECT
          + MEMBER_VIEW_WHERE
          + """
            and (a.businessDate < :cursorBusinessDate
                 or (a.businessDate = :cursorBusinessDate and a.id < :cursorId))
            """
          + MEMBER_VIEW_ORDER)
  List<MemberOrderApplicationView> findMemberViewsAfter(
      @Param("memberId") Long memberId,
      @Param("cursorBusinessDate") LocalDate cursorBusinessDate,
      @Param("cursorId") String cursorId,
      Limit limit);

  /** 会員本人の予約申請 1 件。一覧と同じく申請者の一致を問い合わせに載せ、他人の申請には到達させない。 */
  @Query(MEMBER_VIEW_SELECT + " where a.requesterMemberId = :memberId and a.id = :applicationId")
  Optional<MemberOrderApplicationView> findMemberView(
      @Param("memberId") Long memberId, @Param("applicationId") String applicationId);
}
