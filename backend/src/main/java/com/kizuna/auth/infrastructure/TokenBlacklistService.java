package com.kizuna.auth.infrastructure;

import com.kizuna.shared.config.AppProperties;
import java.time.Duration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * ログアウト用の JWT ブラックリストの読み書き（Redis）。トークン単位（TTL は token 自身の exp まで）とセッション単位（sid）の二つを持つ。
 * アカウント単位の失効は資格情報の版（{@link CredentialVersionService}、ADR 0022）が担い、トークン・セッション単位は版で表現できないため ここに残る。
 */
@Component
public class TokenBlacklistService {

  private static final String KEY_PREFIX = "blacklist:tokens:";
  private static final String SESSION_KEY_PREFIX = "blacklist:sessions:";

  private final RedisTemplate<String, Object> redisTemplate;
  private final AppProperties appProperties;
  private final JwtDecoder expDecoder;

  public TokenBlacklistService(
      RedisTemplate<String, Object> redisTemplate, AppProperties appProperties) {
    this.redisTemplate = redisTemplate;
    this.appProperties = appProperties;
    // 主 JwtDecoder bean（JwtDecoderConfig）は本クラス（TokenBlacklistValidator 経由）に依存するため、
    // ここで注入すると循環参照になる。token の exp を読むためだけの decoder を自前で組み立てる
    // （issuer・ブラックリスト検証は不要 — 失効判定は blacklist() 自身が担う）。
    this.expDecoder =
        NimbusJwtDecoder.withSecretKey(HmacSecretKeyFactory.create(appProperties))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
  }

  /**
   * Authorization ヘッダ値（"Bearer xxx"）または生トークンをブラックリストへ登録する。
   *
   * <p>TTL は token 自身の exp まで（残存有効期間）。token を解析して実際の exp を読むため、運用中に app.jwt.expiration
   * を短縮しても、既発行の長寿命 token が固定 TTL より早くブラックリストから 消えて復活する fail-open は起きない。無効・期限切れの token は書き込みを省略する。
   *
   * @param authHeaderOrToken Authorization ヘッダ値または生トークン（null 可）
   */
  public void blacklist(String authHeaderOrToken) {
    if (authHeaderOrToken == null) {
      return;
    }
    String token =
        authHeaderOrToken.startsWith("Bearer ")
            ? authHeaderOrToken.substring(7)
            : authHeaderOrToken;
    try {
      long ttl =
          expDecoder.decode(token).getExpiresAt().toEpochMilli() - System.currentTimeMillis();
      if (ttl > 0) {
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", Duration.ofMillis(ttl));
      }
    } catch (JwtException e) {
      // 無効・期限切れトークンはブラックリスト不要
    }
  }

  /** 生トークンがブラックリスト登録済みかを返す。 */
  public boolean isBlacklisted(String token) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
  }

  /**
   * ログアウトしたセッションを失効させる。TTL はリフレッシュトークンの有効期間で、同じ sid でそれ以前に発行されたトークンは全てその間に期限が切れる。 トークン文字列ではなく sid
   * で失効させるのは、延長前に盗まれた旧いリフレッシュトークンも同じセッションとして止めるため。
   */
  public void blacklistSession(String sessionId) {
    redisTemplate
        .opsForValue()
        .set(
            SESSION_KEY_PREFIX + sessionId,
            "1",
            Duration.ofMillis(appProperties.getJwt().getRefreshExpiration()));
  }

  public boolean isSessionBlacklisted(String sessionId) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(SESSION_KEY_PREFIX + sessionId));
  }
}
