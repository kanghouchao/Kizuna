package com.kizuna.member.application;

import com.kizuna.member.domain.MemberIdentityView;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.shared.exception.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員コードから会員を引き当てる、店舗側モジュール向けの読み取り口。
 *
 * <p>返すのは会員 ID とコードだけで、表示名などのグループ共通プロフィールは意図的に含めない — 店舗は来店者が提示したコードの実在を確かめられれば足り、
 * 会員本人の登録情報へ到達できてはならない。読むのも実体ではなく projection で、理由は {@link
 * MemberRepository#findIdentityByMemberCode} に記す。
 */
@Service
@RequiredArgsConstructor
public class MemberLookupService {

  private final MemberRepository memberRepository;

  /** 会員コードに対応する会員。存在しなければ空。 */
  @Transactional(readOnly = true)
  public Optional<MemberLookup> findByMemberCode(String memberCode) {
    return memberRepository.findIdentityByMemberCode(memberCode).map(MemberLookupService::toLookup);
  }

  /** ユーザーに対応する会員。会員でなければ空。 */
  @Transactional(readOnly = true)
  public Optional<MemberLookup> findByUserId(Long userId) {
    return memberRepository.findIdentityByUserId(userId).map(MemberLookupService::toLookup);
  }

  /** 会員が退会済みか。退会済み会員の残高は常に零（ADR 0030）で、台帳へ加算を生む操作と帰属の物化はこの述語で分岐する。 会員行の実体は返さない。 */
  @Transactional(readOnly = true)
  public boolean isWithdrawn(long memberId) {
    return memberRepository.isWithdrawn(memberId);
  }

  /**
   * 会員行を押さえる（FOR UPDATE）。残高確認〜確定、関連の成立、手動の加算を、退会（同じ行を先に押さえる）や新規仕訳の挿入と 直列化する。顧客行を押さえる経路は顧客 →
   * 会員の順で取ること（退会は会員だけを押さえるので環にならない）。
   */
  @Transactional
  public void lockForBalanceConfirmation(long memberId) {
    lockMember(memberId);
  }

  /** 同じ会員への「既に受益したか」の判定と新規仕訳の挿入を直列化する（ログイン特典の一回性）。 */
  @Transactional
  public void lockForLedgerWrite(long memberId) {
    lockMember(memberId);
  }

  private void lockMember(long memberId) {
    memberRepository
        .lockIdForBalanceConfirmation(memberId)
        .orElseThrow(() -> new NotFoundException("会員が見つかりません"));
  }

  private static MemberLookup toLookup(MemberIdentityView view) {
    return new MemberLookup(view.getId(), view.getMemberCode());
  }

  /** 店舗側へ渡す会員の最小表現。 */
  public record MemberLookup(Long memberId, String memberCode) {}
}
