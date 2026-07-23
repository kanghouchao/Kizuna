package com.kizuna.shared.storescope;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * 平台トークンが店舗コンソール資格（storeBridge）を持つかを @PreAuthorize から判定する守衛。
 *
 * <p>{@code @storeBridge.check(authentication)} 形式で SpEL から参照する。判定はログイン時に STORE コンソール能力の保持から導出済みの
 * boolean claim（{@code storeBridge}）だけを消費する — shared 層は user モジュールの能力目録に依存せず、{@link
 * StoreIdInterceptor} と同一の教義（導出済み claim のみを見る）に従う。claim 欠落（storeBridge 導入前の旧トークン）は false とし
 * fail-closed に拒否する（旧トークンは 1h TTL で自然失効する）。
 */
@Component("storeBridge")
public class StoreBridgeGuard {

  /** 認証済みリクエストが storeBridge claim を持つか。未認証・claim 欠落・非 JwtAuthenticationToken はいずれも false。 */
  public boolean check(Authentication authentication) {
    Jwt jwt = AuthenticatedJwt.from(authentication);
    if (jwt == null) {
      return false;
    }
    return Boolean.TRUE.equals(jwt.getClaimAsBoolean(StoreIdInterceptor.CLAIM_STORE_BRIDGE));
  }
}
