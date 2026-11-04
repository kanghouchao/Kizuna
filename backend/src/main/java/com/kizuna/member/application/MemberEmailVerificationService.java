package com.kizuna.member.application;

import com.kizuna.member.domain.EmailVerificationToken;
import com.kizuna.member.domain.EmailVerificationTokenRepository;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.exception.TooManyRequestsException;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 本人による検証メールの再送（ADR 0040）。回数は発行の履歴（失効した行を含む）から数え、登録時の発行も 1 回に数える。 */
@Service
@RequiredArgsConstructor
public class MemberEmailVerificationService {

  static final Duration INTERVAL = Duration.ofSeconds(60);
  static final Duration WINDOW = Duration.ofHours(24);
  static final int LIMIT_IN_WINDOW = 10;

  private final UserRepository userRepository;
  private final EmailVerificationTokenRepository tokenRepository;
  private final EmailVerificationMail emailVerificationMail;
  private final Clock clock;

  @Transactional
  public void resend(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"));
    if (user.isEmailVerified()) {
      throw new ServiceException("メールアドレスは確認済みです。画面を再読み込みしてください");
    }
    OffsetDateTime now = OffsetDateTime.now(clock);
    boolean tooSoon =
        tokenRepository
            .findFirstByUserIdOrderByIssuedAtDesc(userId)
            .map(EmailVerificationToken::getIssuedAt)
            .filter(latest -> latest.plus(INTERVAL).isAfter(now))
            .isPresent();
    if (tooSoon
        || tokenRepository.countByUserIdAndIssuedAtAfter(userId, now.minus(WINDOW))
            >= LIMIT_IN_WINDOW) {
      throw tooManyRequests();
    }
    // 生きたトークンの一意索引に当たるのは、並行した再送が先に発行を済ませた場合だけ。
    IntegrityViolations.translateOnFailure(
        () -> {
          emailVerificationMail.issue(user);
          return null;
        },
        Map.of(
            DbConstraint.UQ_T_EMAIL_VERIFICATION_TOKENS_LIVE_USER,
            MemberEmailVerificationService::tooManyRequests));
  }

  private static TooManyRequestsException tooManyRequests() {
    return new TooManyRequestsException("確認メールの再送が続いたため受け付けられませんでした。しばらく時間をおいてからお試しください");
  }
}
