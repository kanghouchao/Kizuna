package com.kizuna.memberconsole.application;

import com.kizuna.customer.application.CustomerMemberLinkDirectory;
import com.kizuna.member.application.MemberDirectoryService;
import com.kizuna.member.application.MemberDirectoryService.MemberProfile;
import com.kizuna.member.application.MemberDirectoryService.MemberSummary;
import com.kizuna.member.application.MemberStatusFilter;
import com.kizuna.memberwithdrawal.application.MemberWithdrawalService;
import com.kizuna.point.application.MemberLedgerQueryService;
import com.kizuna.point.application.MemberLedgerQueryService.LedgerEntry;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreScope;
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
  public Page<MemberSummary> list(MemberStatusFilter status, String search, Pageable pageable) {
    return memberDirectoryService.search(status, search, pageable);
  }

  @Transactional(readOnly = true)
  public MemberDetail detail(long memberId, StoreScope scope) {
    MemberProfile profile =
        memberDirectoryService
            .findProfile(memberId)
            .orElseThrow(() -> new NotFoundException("会員が見つかりません"));
    return new MemberDetail(
        profile,
        pointLedgerService.balance(memberId),
        pointLedgerService.balanceByExpiry(memberId),
        linkDirectory.findByMember(memberId, scope));
  }

  /** 会員の存在は確かめない — 無い会員の明細は空で、照会は詳細が 404 を返す。 */
  @Transactional(readOnly = true)
  public Page<LedgerEntry> entries(long memberId, Pageable pageable) {
    return memberLedgerQueryService.entries(memberId, pageable);
  }

  /** 職員による代行退会。本人退会と同じ編排を操作者 = 職員で呼ぶ。 */
  public void withdraw(long memberId, long actorUserId, String reason) {
    memberWithdrawalService.withdrawByStaff(memberId, actorUserId, reason);
  }
}
