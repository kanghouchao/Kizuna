package com.kizuna.auth.application;

import com.kizuna.auth.api.dto.EmergencyElevationActivationResponse;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.PlatformUserDetails;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.EmergencyElevation;
import com.kizuna.user.domain.EmergencyElevationRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserCredentialsChanged;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 緊急昇格の発動と撤回。担当店舗集合を迂回して単一店舗のデータへ届く短命な昇格を起こす。
 *
 * <p>発動は再認証を伴う。セッションの奪取だけでは越えられない一段を置くためで、再認証は<b>いかなる書き込みよりも先</b>に 実行する —
 * 失敗した再認証が記録を残さないことを、巻き戻しではなく順序で保証する。
 */
@Service
@RequiredArgsConstructor
public class EmergencyElevationService {

  private final EmergencyElevationRepository elevationRepository;
  private final PlatformUserRepository userRepository;
  private final PlatformAuthService authService;
  private final AuthenticationManager authenticationManager;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 宛先の店舗が実在しないことの写像。外部キー違反は全域ハンドラの兜底が 4xx へ落とす対象ではない（一意違反のみ）ため、 flush してこの場で捕まえないと「存在しない店舗への発動」が
   * 500 になる。
   *
   * <p>店舗の指名に 404 を返すのは {@code X-Store-ID} の 403 規律とは別件である。発動できるのは全店舗を見る HQ の
   * 権限保持者で、店舗の実在は元より秘匿対象ではない。
   */
  private static final Map<DbConstraint, Supplier<RuntimeException>> STORE_REFERENCE_VIOLATIONS =
      Map.of(
          DbConstraint.FK_T_EMERGENCY_ELEVATIONS_STORE,
          () -> new NotFoundException("指定された店舗が見つかりません"));

  /** 発動。記録を先に確定させてから、その id と期限を載せた昇格トークンを発行する。 */
  @Transactional
  public EmergencyElevationActivationResponse activate(
      String operatorEmail, Long targetStoreId, String reason, String rawPassword) {
    PlatformUser operator = reauthenticate(operatorEmail, rawPassword);
    EmergencyElevation elevation =
        persist(
            EmergencyElevation.activate(
                operator.getId(), targetStoreId, reason, OffsetDateTime.now()));
    Token token = authService.issueElevatedTokenFor(operator, elevation);
    return new EmergencyElevationActivationResponse(
        elevation.getId(), token.token(), token.expiresAt());
  }

  /**
   * 撤回。記録を閉じたうえで、発動者の資格情報の版を進めて発行済みセッションを失効させる（ADR 0022）。
   *
   * <p>版を進める対象が撤回者ではなく<b>発動者</b>なのは、失効させたいのが昇格トークンだからである。 自己撤回では同一人物になり、その場合も版は 1 度しか進まない。
   *
   * <p>撤回そのものの直列化は記録の楽観ロックに委ねる。同時に走った 2 つ目は版の不一致で 409 になる。
   */
  @Transactional
  public void revoke(Long elevationId, String operatorEmail) {
    EmergencyElevation elevation =
        elevationRepository
            .findById(elevationId)
            .orElseThrow(() -> new NotFoundException("指定された緊急昇格が見つかりません"));
    PlatformUser revoker =
        userRepository
            .findByEmail(operatorEmail)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"));
    elevation.revoke(revoker.getId(), OffsetDateTime.now());
    elevationRepository.save(elevation);

    // 発動者の行は外部キー（NO ACTION）が存在を保証する。引けないのは実装欠陥なので大きく失敗させる。
    PlatformUser activator =
        userRepository
            .findById(elevation.getActivatedBy())
            .orElseThrow(
                () -> new IllegalStateException("緊急昇格の発動者が存在しません: " + elevation.getActivatedBy()));
    activator.invalidateSessions();
    userRepository.save(activator);
    eventPublisher.publishEvent(
        new PlatformUserCredentialsChanged(activator.getEmail(), activator.getCredentialVersion()));
  }

  /**
   * 発動直前の再認証。判定は AuthenticationManager に委ね、失敗（BadCredentialsException 等）はそのまま 401 として 抜ける —
   * ログインと同じ経路・同じ応答で、パスワードの正誤オラクルを新設しない。
   *
   * <p>email の小文字正規化もログインと揃える（保存済み email は全て小文字）。
   */
  private PlatformUser reauthenticate(String email, String rawPassword) {
    Authentication authentication =
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                email.toLowerCase(Locale.ROOT), rawPassword));
    return ((PlatformUserDetails) authentication.getPrincipal()).getPlatformUser();
  }

  /** 記録を書いて id を確定させる。claim に載せる id が要るので、commit を待たず flush する。 */
  private EmergencyElevation persist(EmergencyElevation elevation) {
    try {
      return elevationRepository.saveAndFlush(elevation);
    } catch (DataIntegrityViolationException ex) {
      throw IntegrityViolations.translate(ex, STORE_REFERENCE_VIOLATIONS);
    }
  }
}
