package com.kizuna.auth.application;

import com.kizuna.auth.api.dto.LineAuthorizationRequest;
import com.kizuna.auth.api.dto.LineConfigResponse;
import com.kizuna.auth.api.dto.LineLoginResponse;
import com.kizuna.auth.api.dto.LineRegistrationRequest;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.LineApiClient;
import com.kizuna.auth.infrastructure.LineChannel;
import com.kizuna.auth.infrastructure.LineChannelResolver;
import com.kizuna.auth.infrastructure.LineIdentity;
import com.kizuna.auth.infrastructure.LineRegistrationTicketStore;
import com.kizuna.member.application.MemberRegistrationService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LINE を認証手段とするログイン・登録・連携のユースケース。前端が取得した認可コードをバックエンドが LINE と交換・検証し、 検証済みの LINE ユーザー ID
 * だけを身分の同一性の根拠にする。
 *
 * <p>なりすまし防止の不変条件: ログイン経路は LINE ユーザー ID でしか身分を引かない。LINE 側の検証済みメールアドレスが 既存アカウントと一致しても自動連携はせず、未知の
 * LINE ユーザー ID は必ず登録チケット経路へ落とす（メール一致は本人性の 証明にならず、自動連携を許すと LINE 側でメールを詐称できる限りアカウント乗っ取りになる）。
 *
 * <p>発行するトークンはパスワードログインと同一の {@link PlatformAuthService#issueTokenFor} を通す（認証手段による claim の齟齬を作らない）。
 */
@Service
@RequiredArgsConstructor
public class LineAuthService {

  private static final String LINE_DISABLED_MESSAGE = "LINE ログインは利用できません";
  private static final String INVALID_TICKET_MESSAGE = "登録チケットが無効または期限切れです。最初からやり直してください";
  private static final String LINE_TAKEN_MESSAGE = "この LINE アカウントは既に別の身分と連携済みです";
  private static final String LINE_USER_UNIQUE_CONSTRAINT = "uq_t_users_line_user_id";

  private final LineChannelResolver channelResolver;
  private final LineApiClient lineApiClient;
  private final LineRegistrationTicketStore ticketStore;
  private final PlatformUserRepository userRepository;
  private final MemberRegistrationService memberRegistrationService;
  private final PlatformAuthService authService;

  /** 前端が認可要求を組み立てるための公開設定。無効なら channelId は返さない。 */
  @Transactional(readOnly = true)
  public LineConfigResponse config() {
    return channelResolver
        .resolve()
        .map(channel -> new LineConfigResponse(true, channel.channelId()))
        .orElseGet(() -> new LineConfigResponse(false, null));
  }

  /** LINE ログイン。連携済みならトークンを、未登録なら 1 度きりの登録チケットを返す。 */
  @Transactional(readOnly = true)
  public LineLoginResponse login(LineAuthorizationRequest request) {
    LineIdentity identity = verify(request);
    return userRepository
        .findByLineUserId(identity.lineUserId())
        .map(this::issueTokenForLinkedUser)
        .orElseGet(
            () ->
                LineLoginResponse.unregistered(
                    ticketStore.issue(identity.lineUserId()), identity.displayName()));
  }

  /** 登録チケットで会員身分を作成し、そのままログイン状態にする（自動ログイン）。 */
  @Transactional
  public Token register(LineRegistrationRequest request) {
    String lineUserId =
        ticketStore
            .peek(request.getRegistrationTicket())
            .orElseThrow(() -> new ServiceException(INVALID_TICKET_MESSAGE));
    PlatformUser user =
        memberRegistrationService.registerWithLine(
            request.getEmail(), request.getDisplayName(), lineUserId);
    // 消費は登録成功後 — 重複メールなどの失敗でチケットを失うと、入力を直すだけのやり直しに OAuth の再実行を強いるため。
    ticketStore.consume(request.getRegistrationTicket());
    return authService.issueTokenFor(user);
  }

  /** 認証済みの本人に LINE アカウントを連携する。連携の解除・付け替えは提供しない。 */
  @Transactional
  public void link(String email, LineAuthorizationRequest request) {
    LineIdentity identity = verify(request);
    PlatformUser user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"));
    // 連携先を取り合うのは未連携の身分だけ。連携済み本人の再連携は、相手が同じ LINE アカウントであっても
    // linkLine が集約の不変条件として拒否する（付け替えを提供しないため、理由の表示も本人側の事実に揃える）。
    if (user.getLineUserId() == null && userRepository.existsByLineUserId(identity.lineUserId())) {
      throw new ConflictException(LINE_TAKEN_MESSAGE);
    }
    user.linkLine(identity.lineUserId());
    try {
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException ex) {
      // 事前チェックを擦り抜けた並行連携。ここで違反し得る一意制約は LINE ユーザー ID だけであり、
      // 「先に確定した別要求と衝突した」なので 409 に写像する。
      String cause = ex.getMostSpecificCause().getMessage();
      if (cause != null && cause.contains(LINE_USER_UNIQUE_CONSTRAINT)) {
        throw new ConflictException(LINE_TAKEN_MESSAGE);
      }
      throw ex;
    }
  }

  /** 認可コードを LINE と交換し、id_token を LINE に検証させて本人同一性を得る。 */
  private LineIdentity verify(LineAuthorizationRequest request) {
    LineChannel channel =
        channelResolver
            .resolve()
            .orElseThrow(() -> new ServiceUnavailableException(LINE_DISABLED_MESSAGE));
    return lineApiClient.exchangeAndVerify(
        channel, request.getCode(), request.getRedirectUri(), request.getCodeVerifier());
  }

  /** 連携済み身分へのトークン発行。停止済みアカウントはパスワードログインと同じく 401 で拒否する。 */
  private LineLoginResponse issueTokenForLinkedUser(PlatformUser user) {
    if (!Boolean.TRUE.equals(user.getEnabled())) {
      throw new DisabledException("アカウントが無効化されています");
    }
    return LineLoginResponse.registered(authService.issueTokenFor(user));
  }
}
