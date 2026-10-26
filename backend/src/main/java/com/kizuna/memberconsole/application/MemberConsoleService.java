package com.kizuna.memberconsole.application;

import com.kizuna.customer.application.CustomerMemberLinkDirectory;
import com.kizuna.customer.application.CustomerMemberLinkDirectory.MemberLinks;
import com.kizuna.member.application.MemberDirectoryService;
import com.kizuna.member.application.MemberDirectoryService.MemberProfile;
import com.kizuna.member.application.MemberStatusFilter;
import com.kizuna.memberconsole.api.dto.MemberDetailResponse;
import com.kizuna.memberconsole.api.dto.MemberLedgerEntryResponse;
import com.kizuna.memberconsole.api.dto.MemberSummaryResponse;
import com.kizuna.memberwithdrawal.application.MemberWithdrawalService;
import com.kizuna.point.application.MemberLedgerQueryService;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.web.CursorPage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員コンソールのユースケース。照会は各所有モジュールの読み口を一つの応答に合成し、代行退会は退会の編排へ委ねる。
 *
 * <p>関連だけが閲覧者の店舗集合で絞られる。会員・ランク・台帳はグループ級の資産で店舗集合に依らない。
 */
@Service
@RequiredArgsConstructor
public class MemberConsoleService {

  private final MemberDirectoryService memberDirectoryService;
  private final PointLedgerService pointLedgerService;
  private final MemberLedgerQueryService memberLedgerQueryService;
  private final CustomerMemberLinkDirectory linkDirectory;
  private final MemberWithdrawalService memberWithdrawalService;

  @Transactional(readOnly = true)
  public Page<MemberSummaryResponse> list(
      MemberStatusFilter status, String search, Pageable pageable) {
    return memberDirectoryService
        .search(status, search, pageable)
        .map(
            row ->
                new MemberSummaryResponse(
                    row.id(),
                    row.memberCode(),
                    row.displayName(),
                    row.email(),
                    row.rank(),
                    row.registeredAt(),
                    row.withdrawnAt()));
  }

  @Transactional(readOnly = true)
  public MemberDetailResponse detail(long memberId, StoreScope scope) {
    MemberProfile profile =
        memberDirectoryService
            .findProfile(memberId)
            .orElseThrow(() -> new NotFoundException("会員が見つかりません"));
    MemberLinks links = linkDirectory.findByMember(memberId, scope);
    return new MemberDetailResponse(
        profile.id(),
        profile.memberCode(),
        profile.displayName(),
        profile.email(),
        profile.lineLinked(),
        profile.rank(),
        profile.registeredAt(),
        profile.withdrawnAt() == null
            ? null
            : new MemberDetailResponse.Withdrawal(
                profile.withdrawnAt(),
                profile.withdrawalReason(),
                profile.withdrawnBySelf(),
                profile.withdrawnByName()),
        pointLedgerService.balance(memberId),
        pointLedgerService.balanceByExpiry(memberId).stream()
            .map(b -> new MemberDetailResponse.ExpiryBalance(b.expiresOn(), b.points()))
            .toList(),
        profile.rankHistory().stream()
            .map(
                t ->
                    new MemberDetailResponse.RankTransition(
                        t.previousRank(), t.newRank(), t.promotedAt()))
            .toList(),
        toLinks(links),
        links.otherStoreCount());
  }

  /** 明細の続きはカーソルで辿る。会員の存在は確かめない — 無い会員の明細は空で、照会は詳細が 404 を返す。 */
  @Transactional(readOnly = true)
  public CursorPage<MemberLedgerEntryResponse> entries(long memberId, String cursor, int size) {
    return memberLedgerQueryService
        .entries(memberId, cursor, size)
        .map(
            e ->
                new MemberLedgerEntryResponse(
                    e.occurredAt(),
                    e.entryType().name(),
                    e.amount(),
                    e.expiresOn(),
                    e.storeName(),
                    e.reason(),
                    e.actorName()));
  }

  /** 職員による代行退会。本人退会と同じ編排を操作者 = 職員で呼ぶ。 */
  public void withdraw(long memberId, long actorUserId, String reason) {
    memberWithdrawalService.withdrawByStaff(memberId, actorUserId, reason);
  }

  private static List<MemberDetailResponse.Link> toLinks(MemberLinks links) {
    return links.segments().stream()
        .map(
            s ->
                new MemberDetailResponse.Link(
                    s.id(),
                    s.storeId(),
                    s.storeName(),
                    s.customerId(),
                    s.customerName(),
                    s.status().name(),
                    s.reason().name(),
                    s.linkedAt(),
                    s.releasedAt(),
                    s.releaseReason()))
        .toList();
  }
}
