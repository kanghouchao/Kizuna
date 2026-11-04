package com.kizuna.member.application;

import com.kizuna.member.domain.EmailVerificationToken;
import com.kizuna.member.domain.EmailVerificationTokenRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.Timestamps;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 検証メールのリンクによるメールアドレスの確認（ADR 0040）。匿名で呼ばれ、トークンだけが本人の根拠になる。
 *
 * <p>拒否の理由（不明・消費済み・失効・期限切れ・宛先の不一致・退会済み）は同じ文言で返し、どのトークンがどの状態かを外から引けないようにする。
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationConfirmation {

  static final String REJECTED = "確認リンクが無効か、有効期限が切れています。";

  private final EmailVerificationTokenRepository tokenRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  /** トークンを消費して、発行時の宛先を検証済みにする。宛先が変わっていれば古い宛先のトークンでは検証しない。 */
  @Transactional
  public void confirm(String token) {
    EmailVerificationToken verification =
        tokenRepository
            .findByTokenDigestForUpdate(EmailVerificationMail.digest(token))
            .filter(found -> found.isUsable(OffsetDateTime.now(clock)))
            .orElseThrow(() -> new ServiceException(REJECTED));
    User user =
        userRepository
            .findById(verification.getUserId())
            .filter(found -> !found.isWithdrawn())
            .filter(found -> verification.getEmail().equals(found.getEmail()))
            .orElseThrow(() -> new ServiceException(REJECTED));
    OffsetDateTime now = Timestamps.now();
    verification.consume(now);
    user.markEmailVerified(now);
  }
}
