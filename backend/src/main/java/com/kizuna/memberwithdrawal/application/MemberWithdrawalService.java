package com.kizuna.memberwithdrawal.application;

import com.kizuna.customer.application.CustomerMemberLinkWithdrawal;
import com.kizuna.member.application.MemberWithdrawal;
import com.kizuna.member.application.MemberWithdrawal.WithdrawnMember;
import com.kizuna.order.withdrawal.MemberWithdrawalApplications;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.point.application.PointLedgerService.WithdrawalClearance;
import com.kizuna.user.application.BusinessAudit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員退会の編排（GLOSSARY「会員退会」）。会員身分の終端化・利用者の停止・ポイントの退会消去・全 ACTIVE 関連の解除・PENDING
 * 予約申請の取り下げを一つの取引で確定し、どれかが失敗すれば全部を巻き戻す。
 *
 * <p>member / point / customer / order のどれにも置けない — 下位の member は上位三者に依存できず、上位の三者は互いの操作を知らない。
 * 失効処理（pointsexpiry）と同じく、編排だけを担う独立のモジュールにする。
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

  /** 退会時の消去仕訳の理由。巻き戻しの再消去（{@link PointLedgerService#WITHDRAWN_RECLEAR_REASON}）と明細上で区別する。 */
  static final String CLEAR_REASON = "会員退会";

  private final MemberWithdrawal memberWithdrawal;
  private final PointLedgerService pointLedgerService;
  private final CustomerMemberLinkWithdrawal linkWithdrawal;
  private final MemberWithdrawalApplications applicationWithdrawal;
  private final BusinessAudit audit;

  /** 本人による退会。会員行のロックが先頭にあり、以後の台帳・関連・申請の書き込みはその直列化の下で進む。 */
  @Transactional
  public void withdrawSelf(long userId, String reason) {
    WithdrawnMember withdrawn = memberWithdrawal.withdrawSelf(userId, reason);
    completeWithdrawal(withdrawn, withdrawn.userId());
  }

  /** 職員による代行退会。効果は本人退会と同じで、消去仕訳・関連解除・申請取り下げの操作者を職員の利用者 ID で記録する。 */
  @Transactional
  public void withdrawByStaff(long memberId, long actorUserId, String reason) {
    WithdrawnMember withdrawn = memberWithdrawal.withdrawByStaff(memberId, actorUserId, reason);
    completeWithdrawal(withdrawn, actorUserId);
  }

  private void completeWithdrawal(WithdrawnMember withdrawn, long actorUserId) {
    WithdrawalClearance cleared =
        pointLedgerService.clearForWithdrawal(withdrawn.memberId(), actorUserId, CLEAR_REASON);
    linkWithdrawal.releaseAll(withdrawn.memberId(), actorUserId, withdrawn.withdrawnAt());
    int withdrawnApplications =
        applicationWithdrawal.withdrawPending(
            withdrawn.memberId(), actorUserId, withdrawn.withdrawnAt());
    // 理由の原文は写さない。関連解除は解除側が行ごとに記録済みなので、ここでは数えない。
    // 消去仕訳が分かれたときの source は先頭の仕訳。合計は cleared_points に載る。
    audit.recordById(
        actorUserId,
        null,
        "MEMBER_WITHDRAWN",
        "MEMBER",
        Long.toString(withdrawn.memberId()),
        cleared.firstEntryId().isPresent() ? "POINT_LEDGER_ENTRY" : null,
        cleared.firstEntryId().map(Object::toString).orElse(null),
        Map.of(
            "status", "ACTIVE",
            "version", Long.toString(withdrawn.versionBefore()),
            "user_enabled", Boolean.toString(withdrawn.userEnabledBefore())),
        Map.of(
            "status", "WITHDRAWN",
            "version", Long.toString(withdrawn.versionAfter()),
            "withdrawn_at", withdrawn.withdrawnAt().toString(),
            "user_enabled", "false",
            "changed_fields", "withdrawn_at,withdrawn_by,withdrawal_reason",
            "cleared_points", Long.toString(cleared.points()),
            "withdrawn_applications", Integer.toString(withdrawnApplications)));
  }
}
