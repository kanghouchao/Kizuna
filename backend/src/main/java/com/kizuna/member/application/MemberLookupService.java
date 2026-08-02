package com.kizuna.member.application;

import com.kizuna.member.domain.MemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員コードから会員を引き当てる、店舗側モジュール向けの読み取り口。
 *
 * <p>返すのは会員 ID とコードだけで、表示名などのプラットフォーム側プロフィールは意図的に含めない — 店舗は来店者が提示したコードの実在を確かめられれば足り、
 * 会員本人の登録情報へ到達できてはならない。
 */
@Service
@RequiredArgsConstructor
public class MemberLookupService {

  private final MemberRepository memberRepository;

  /** 会員コードに対応する会員。存在しなければ空。 */
  @Transactional(readOnly = true)
  public Optional<MemberLookup> findByMemberCode(String memberCode) {
    return memberRepository
        .findByMemberCode(memberCode)
        .map(member -> new MemberLookup(member.getId(), member.getMemberCode()));
  }

  /** 店舗側へ渡す会員の最小表現。 */
  public record MemberLookup(Long memberId, String memberCode) {}
}
