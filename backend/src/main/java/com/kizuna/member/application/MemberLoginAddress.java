package com.kizuna.member.application;

import com.kizuna.member.domain.MemberRepository;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 会員宛の通知の宛先。会員のログイン用メールアドレスとその検証の有無、退会の有無だけを返し、表示名などのプロフィールは渡さない。 */
@Service
@RequiredArgsConstructor
public class MemberLoginAddress {

  private final MemberRepository memberRepository;
  private final UserRepository userRepository;

  public record Address(String email, boolean emailVerified, boolean withdrawn) {}

  /** 会員が存在しなければ空。 */
  @Transactional(readOnly = true)
  public Optional<Address> of(long memberId) {
    return memberRepository
        .findById(memberId)
        .map(
            member -> {
              User user =
                  userRepository
                      .findById(member.getUserId())
                      .orElseThrow(() -> new IllegalStateException("会員の利用者行が存在しません"));
              return new Address(user.getEmail(), user.isEmailVerified(), member.isWithdrawn());
            });
  }
}
