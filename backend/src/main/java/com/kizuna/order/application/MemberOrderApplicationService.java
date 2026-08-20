package com.kizuna.order.application;

import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.order.api.dto.MemberOrderApplicationCreateRequest;
import com.kizuna.order.api.dto.MemberOrderApplicationResponse;
import com.kizuna.order.domain.MemberOrderApplicationView;
import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeExempt;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員ポータルからの予約申請ユースケース。申請は受注（Order）ではなく予約申請（OrderApplication）の PENDING として起きる（ADR 0017）。
 *
 * <p>会員は店舗を授権されないため店舗文脈（{@code @StoreScoped}）を確立できず、storeFilter も働かない。したがって
 *
 * <ul>
 *   <li>書き込みは店舗の実在を確かめたうえで store_id を明示設定する（{@code StoreScopeStampListener} は設定済みの store_id を尊重する）
 *   <li>読み取り・取り下げは申請者（requesterMemberId）の一致が唯一の隔離境界であり、必ず問い合わせ自体に載せる
 * </ul>
 *
 * <p>申請できる店舗は絞らない（紐づけ済みの店舗に限らない）。会員が初めて訪れる店舗にも申請できることが業務上の既定で、顧客台帳との紐づけは 店舗の確定時の自動整備が「今の関連」だけを見て行う。
 */
@Service
@RequiredArgsConstructor
public class MemberOrderApplicationService {

  private final OrderApplicationRepository orderApplicationRepository;
  private final OrderApplicationIntake orderApplicationIntake;
  private final PlatformUserRepository platformUserRepository;
  private final MemberLookupService memberLookupService;
  private final StoreExistenceCheck storeExistenceCheck;
  private final BusinessDateService businessDateService;

  /** 予約を申請する。予約申請の PENDING 行だけが起き、受注（t_orders）には行が生まれない。 */
  @StoreScopeExempt(reason = "会員は店舗文脈を確立できないため、店舗の実在を確かめたうえで store_id を明示設定して書く")
  @Transactional
  public MemberOrderApplicationResponse request(
      String email, MemberOrderApplicationCreateRequest request) {
    MemberLookup member = resolveMember(email);
    Long storeId = request.getStoreId();
    if (!storeExistenceCheck.exists(storeId)) {
      throw new ServiceException("店舗が見つかりません");
    }
    orderApplicationIntake.validateRequestedVisit(
        storeId, request.getBusinessDate(), request.getCastId());

    OrderApplication application =
        OrderApplication.builder()
            .status(OrderApplicationStatus.PENDING)
            .businessDate(request.getBusinessDate())
            .arrivalScheduledStartTime(request.getArrivalScheduledStartTime())
            .pax(request.getPax())
            .castId(request.getCastId())
            .remarks(request.getRemarks())
            .requesterMemberId(member.memberId())
            .requesterMemberCode(member.memberCode())
            .requesterDeclaredName(request.getDeclaredName())
            .build();
    // 店舗文脈が無い経路のため store_id を明示する。
    application.setStoreId(storeId);

    OrderApplication saved = orderApplicationRepository.save(application);
    return toResponse(member.memberId(), saved.getId());
  }

  /**
   * 本人が申請した予約の一覧（跨店集約）。
   *
   * <p>続きの指定はカーソル（並びの鍵）で受ける。取り下げで行が消えることはないが、確定・謝絶の反映で 並びが動きうる一覧を件数で指すと、続きを取った時点で境界の申請を飛ばす。
   *
   * @param cursor 続きの位置。null なら先頭から
   * @param requestedSize 1 回に返す件数の希望値（上限に丸められる）
   */
  @StoreScopeExempt(reason = "会員は店舗文脈を確立できないため、申請者（requesterMemberId）の一致を問い合わせ自体に載せることが唯一の境界")
  @Transactional(readOnly = true)
  public CursorPage<MemberOrderApplicationResponse> list(
      String email, String cursor, int requestedSize) {
    MemberLookup member = resolveMember(email);
    int size = CursorPage.clampSize(requestedSize);
    // 続きの有無は上限より 1 件多く取って判る。総件数の問い合わせを毎回撒かずに済む。
    Limit limit = Limit.of(size + 1);
    List<MemberOrderApplicationView> fetched =
        cursor == null
            ? orderApplicationRepository.findMemberViews(member.memberId(), limit)
            : fetchAfter(member.memberId(), PageCursor.decode(cursor), limit);
    LocalDate today = businessDateService.currentBusinessDate();
    return CursorPage.of(fetched, size, MemberOrderApplicationService::cursorOf)
        .map(view -> toResponse(view, today));
  }

  private List<MemberOrderApplicationView> fetchAfter(
      Long memberId, PageCursor cursor, Limit limit) {
    // 続きの取得でも申請者の一致は問い合わせに載せ続ける — カーソルは位置を指すだけで、隔離境界にはならない。
    return orderApplicationRepository.findMemberViewsAfter(
        memberId, cursor.dateKey(), cursor.id(), limit);
  }

  /** 続きの位置は一覧の並び（希望日 + id）と同じ組で作る。組が並びとずれると、続きが手前へ戻るか行を飛ばす。 */
  private static String cursorOf(MemberOrderApplicationView view) {
    return new PageCursor(view.getBusinessDate().toString(), view.getId()).encode();
  }

  /** 本人が申請した未処理の予約を取り下げる。確定・謝絶の後は動かせない（終端）。 */
  @StoreScopeExempt(reason = "会員は店舗文脈を確立できないため、申請者（requesterMemberId）の一致を取り出しの条件に載せることが唯一の境界")
  @Transactional
  public MemberOrderApplicationResponse withdraw(String email, String applicationId) {
    Long platformUserId = resolvePlatformUserId(email);
    MemberLookup member = resolveMemberByPlatformUserId(platformUserId);
    OrderApplication application = ownedApplication(member.memberId(), applicationId);
    application.withdraw(platformUserId, OffsetDateTime.now());
    orderApplicationRepository.save(application);
    return toResponse(member.memberId(), applicationId);
  }

  /** 本人が申請した予約を取り出す。他人の申請・存在しない id は「見つからない」として扱う — 権限違反として区別すると、その id の申請が存在することそのものが分かってしまう。 */
  private OrderApplication ownedApplication(Long memberId, String applicationId) {
    return orderApplicationRepository
        .findById(applicationId)
        .filter(application -> memberId.equals(application.getRequesterMemberId()))
        .orElseThrow(() -> new NotFoundException("予約申請が見つかりません: " + applicationId));
  }

  private MemberLookup resolveMember(String email) {
    return resolveMemberByPlatformUserId(resolvePlatformUserId(email));
  }

  private Long resolvePlatformUserId(String email) {
    return platformUserRepository
        .findByEmail(email)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
        .getId();
  }

  private MemberLookup resolveMemberByPlatformUserId(Long platformUserId) {
    return memberLookupService
        .findByPlatformUserId(platformUserId)
        .orElseThrow(() -> new StaleSessionException("会員情報が存在しません"));
  }

  private MemberOrderApplicationResponse toResponse(Long memberId, String applicationId) {
    LocalDate today = businessDateService.currentBusinessDate();
    return orderApplicationRepository
        .findMemberView(memberId, applicationId)
        .map(view -> toResponse(view, today))
        .orElseThrow(() -> new NotFoundException("予約申請が見つかりません: " + applicationId));
  }

  private MemberOrderApplicationResponse toResponse(
      MemberOrderApplicationView view, LocalDate currentBusinessDate) {
    return MemberOrderApplicationResponse.builder()
        .id(view.getId())
        .storeId(view.getStoreId())
        .storeName(view.getStoreName())
        .businessDate(view.getBusinessDate())
        .arrivalScheduledStartTime(view.getArrivalScheduledStartTime())
        .pax(view.getPax())
        .castName(view.getCastName())
        .status(view.getStatus() == null ? null : view.getStatus().name())
        .expired(
            OrderApplication.isExpired(
                view.getStatus(), view.getBusinessDate(), currentBusinessDate))
        .build();
  }
}
