package com.kizuna.order.application;

import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.order.domain.MemberOrderApplicationView;
import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.user.application.ActorIdentityService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員ポータルからの予約申請ユースケース。申請は受注（Order）ではなく予約申請（OrderApplication）の PENDING として起きる（ADR 0017）。
 *
 * <p>会員は店舗を授権されないため店舗文脈を持たない。したがって
 *
 * <ul>
 *   <li>書き込みは店舗の実在を確かめたうえで store_id を明示設定する
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
  private final ActorIdentityService actorIdentityService;
  private final MemberLookupService memberLookupService;
  private final StoreExistenceCheck storeExistenceCheck;
  private final BusinessDateService businessDateService;

  /** 予約を申請する。予約申請の PENDING 行だけが起き、受注（t_orders）には行が生まれない。 */
  @Transactional
  public MemberOrderApplicationResult request(String email, MemberOrderApplicationCommand command) {
    MemberLookup member = resolveMember(email);
    // 退会と並行する申請が退会の commit 後に PENDING のまま成立しないよう、退会と同じ会員行を押さえてから退会状態を見る。
    memberLookupService.lockForBalanceConfirmation(member.memberId());
    if (memberLookupService.isWithdrawn(member.memberId())) {
      throw new StaleSessionException("会員情報が存在しません");
    }
    Long storeId = command.storeId();
    if (!storeExistenceCheck.exists(storeId)) {
      throw new ServiceException("店舗が見つかりません");
    }
    orderApplicationIntake.validateRequestedVisit(
        storeId, command.businessDate(), command.castId());

    OrderApplication application =
        OrderApplication.builder()
            .status(OrderApplicationStatus.PENDING)
            .businessDate(command.businessDate())
            .arrivalScheduledStartTime(command.arrivalScheduledStartTime())
            .pax(command.pax())
            .castId(command.castId())
            .remarks(command.remarks())
            .requesterMemberId(member.memberId())
            .requesterMemberCode(member.memberCode())
            .requesterDeclaredName(command.declaredName())
            .build();
    application.setStoreId(storeId);

    OrderApplication saved = orderApplicationRepository.save(application);
    return toResult(member.memberId(), saved.getId());
  }

  /** 本人が申請した予約の一覧（跨店集約）。 */
  @Transactional(readOnly = true)
  public Page<MemberOrderApplicationResult> list(String email, Pageable pageable) {
    MemberLookup member = resolveMember(email);
    LocalDate today = businessDateService.currentBusinessDate();
    return orderApplicationRepository
        .findMemberViews(member.memberId(), pageable)
        .map(view -> toResult(view, today));
  }

  /** 本人が申請した未処理の予約を取り下げる。確定・謝絶の後は動かせない（終端）。 */
  @Transactional
  public MemberOrderApplicationResult withdraw(String email, String applicationId) {
    Long userId = actorIdentityService.requireUserId(email);
    MemberLookup member = resolveMemberByUserId(userId);
    OrderApplication application = ownedApplication(member.memberId(), applicationId);
    application.withdraw(userId, OffsetDateTime.now());
    orderApplicationRepository.save(application);
    return toResult(member.memberId(), applicationId);
  }

  /** 本人が申請した予約を取り出す。他人の申請・存在しない id は「見つからない」として扱う — 権限違反として区別すると、その id の申請が存在することそのものが分かってしまう。 */
  private OrderApplication ownedApplication(Long memberId, String applicationId) {
    return orderApplicationRepository
        .findByIdAndRequesterMemberId(applicationId, memberId)
        .orElseThrow(() -> new NotFoundException("予約申請が見つかりません: " + applicationId));
  }

  private MemberLookup resolveMember(String email) {
    return resolveMemberByUserId(actorIdentityService.requireUserId(email));
  }

  private MemberLookup resolveMemberByUserId(Long userId) {
    return memberLookupService
        .findByUserId(userId)
        .orElseThrow(() -> new StaleSessionException("会員情報が存在しません"));
  }

  private MemberOrderApplicationResult toResult(Long memberId, String applicationId) {
    LocalDate today = businessDateService.currentBusinessDate();
    return orderApplicationRepository
        .findMemberView(memberId, applicationId)
        .map(view -> toResult(view, today))
        .orElseThrow(() -> new NotFoundException("予約申請が見つかりません: " + applicationId));
  }

  private MemberOrderApplicationResult toResult(
      MemberOrderApplicationView view, LocalDate currentBusinessDate) {
    return new MemberOrderApplicationResult(
        view.getId(),
        view.getStoreId(),
        view.getStoreName(),
        view.getBusinessDate(),
        view.getArrivalScheduledStartTime(),
        view.getPax(),
        view.getCastName(),
        view.getStatus(),
        OrderApplication.isExpired(view.getStatus(), view.getBusinessDate(), currentBusinessDate));
  }
}
