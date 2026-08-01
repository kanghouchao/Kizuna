package com.kizuna.member.application;

import com.kizuna.member.api.dto.MemberHomeResponse;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 本人（会員）ポータルのホーム解決ユースケース。認証主体の email から本人の会員コードのみを返す（他人のデータへの到達経路を持たない）。 */
@Service
@RequiredArgsConstructor
public class MemberSelfService {

  private final PlatformUserRepository platformUserRepository;
  private final MemberRepository memberRepository;

  @Transactional(readOnly = true)
  public MemberHomeResponse home(String email) {
    PlatformUser user =
        platformUserRepository
            .findByEmail(email)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"));
    return memberRepository
        .findByPlatformUserId(user.getId())
        .map(member -> new MemberHomeResponse(member.getMemberCode(), user.getDisplayName()))
        .orElseThrow(() -> new StaleSessionException("会員情報が存在しません"));
  }
}
