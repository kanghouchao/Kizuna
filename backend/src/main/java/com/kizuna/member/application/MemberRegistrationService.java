package com.kizuna.member.application;

import com.kizuna.member.api.dto.MemberRegistrationRequest;
import com.kizuna.member.api.dto.MemberRegistrationResponse;
import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberCodes;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員の自助登録ユースケース。メール + パスワード + 表示名で MEMBER 身分と会員集約（一意の会員コード付き）を単一トランザクションで作成する。
 *
 * <p>登録入口から生まれる身分は会員のみ（キャスト = 招待受諾、スタッフ = 管理者作成が成立経路）。作成する MEMBER は SPECIFIC_STORES +
 * 空集合（登録時点で紐づけ店舗なし）で、どの店舗も授権しない。
 */
@Service
@RequiredArgsConstructor
public class MemberRegistrationService {

  private static final String DUPLICATE_EMAIL_MESSAGE = "このメールアドレスは既に登録されています。ログインしてご利用ください";
  private static final String DUPLICATE_LINE_USER_MESSAGE = "この LINE アカウントは既に別の身分と連携済みです";

  /** 会員コード発行の再試行上限。数字 12 桁空間では衝突は実質発生せず、上限到達は乱数源の異常を意味する。 */
  private static final int CODE_ISSUE_ATTEMPTS = 5;

  private final PlatformUserRepository platformUserRepository;
  private final MemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;

  private final SecureRandom random = new SecureRandom();

  @Transactional
  public MemberRegistrationResponse register(MemberRegistrationRequest request) {
    if (platformUserRepository
        .findByEmail(request.getEmail().toLowerCase(Locale.ROOT))
        .isPresent()) {
      throw new ServiceException(DUPLICATE_EMAIL_MESSAGE);
    }
    PlatformUser user =
        saveUser(
            PlatformUser.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .enabled(true)
                .userType(UserType.MEMBER)
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of())
                .build());
    Member member =
        memberRepository.save(
            Member.builder().memberCode(issueCode()).platformUserId(user.getId()).build());
    return new MemberRegistrationResponse(member.getMemberCode());
  }

  /**
   * LINE 認証で確定した身分の会員登録。パスワードは推測不能な乱数を符号化して置く — LINE のみで登録した会員は
   * パスワードログインの経路を持たない（本人が忘れて困る値ではなく、MEMBER に資格情報を要求する 不変条件を満たすための値）。
   *
   * <p>重複は 409 で返す（{@link #register} の 400 と異なる）。LINE 登録は前端が既存アカウントとの衝突を検出して 案内へ分岐する必要があり、入力形式の誤り
   * （400）と区別できなければならない。
   *
   * @param lineUserId LINE が検証した LINE ユーザー ID
   * @return 作成された会員身分のプラットフォームユーザー
   */
  @Transactional
  public PlatformUser registerWithLine(String email, String displayName, String lineUserId) {
    if (platformUserRepository.findByEmail(email.toLowerCase(Locale.ROOT)).isPresent()) {
      throw new ConflictException(DUPLICATE_EMAIL_MESSAGE);
    }
    if (platformUserRepository.existsByLineUserId(lineUserId)) {
      throw new ConflictException(DUPLICATE_LINE_USER_MESSAGE);
    }
    PlatformUser user =
        saveLineUser(
            PlatformUser.builder()
                .email(email)
                .password(passwordEncoder.encode(randomPassword()))
                .displayName(displayName)
                .enabled(true)
                .userType(UserType.MEMBER)
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of())
                .lineUserId(lineUserId)
                .build());
    // flush まで行い、会員行の制約違反も呼び出し元が登録チケットを消費する前に顕在化させる
    memberRepository.saveAndFlush(
        Member.builder().memberCode(issueCode()).platformUserId(user.getId()).build());
    return user;
  }

  /** パスワードログインを成立させないための乱数パスワード（符号化前の平文はどこにも残さない）。 */
  private String randomPassword() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** 一意の会員コードを発行する。事前の存在チェックで衝突を避け、上限到達は例外（並行登録との厳密な競合は DB の一意制約が最終防衛線として 409 で顕在化する）。 */
  private String issueCode() {
    for (int attempt = 0; attempt < CODE_ISSUE_ATTEMPTS; attempt++) {
      String code = MemberCodes.generate(random);
      if (!memberRepository.existsByMemberCode(code)) {
        return code;
      }
    }
    throw new IllegalStateException("会員コードの発行に失敗しました");
  }

  /**
   * 事前チェック後の並行登録で同一 email が先に確定した場合、一意制約違反を重複登録の 400 に写像する（事前チェックだけでは競合を塞げないための最終防衛線）。 saveAndFlush
   * で INSERT をここで確定させ、コミット時の遅延フラッシュが 500 に化けるのを防ぐ。
   */
  private PlatformUser saveUser(PlatformUser user) {
    try {
      return platformUserRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException ex) {
      throw IntegrityViolations.translate(
          ex,
          Map.of(
              DbConstraint.UQ_T_USERS_EMAIL, () -> new ServiceException(DUPLICATE_EMAIL_MESSAGE)));
    }
  }

  /**
   * LINE 登録の保存。事前チェックを擦り抜けた並行登録を、email・LINE ユーザー ID いずれの一意制約違反も 409 へ写像する
   * （どちらも「先に確定した別要求と衝突した」であり、要求自体の形式は正しい）。
   */
  private PlatformUser saveLineUser(PlatformUser user) {
    try {
      return platformUserRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException ex) {
      throw IntegrityViolations.translate(
          ex,
          Map.of(
              DbConstraint.UQ_T_USERS_EMAIL,
              () -> new ConflictException(DUPLICATE_EMAIL_MESSAGE),
              DbConstraint.UQ_T_USERS_LINE_USER_ID,
              () -> new ConflictException(DUPLICATE_LINE_USER_MESSAGE)));
    }
  }
}
