package com.kizuna.auth.infrastructure;

import com.kizuna.auth.api.dto.Token;
import com.kizuna.shared.config.AppProperties;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** プラットフォームドメインの Bearer トークン発行。署名は HS256 に固定し、{@link JwtDecoderConfig} の decoder と揃える。 */
@Component
@RequiredArgsConstructor
public class PlatformJwtIssuer {

  /** プラットフォームドメインのトークン発行者 */
  public static final String ISSUER_PLATFORM = "PlatformAuth";

  private final JwtEncoder jwtEncoder;
  private final AppProperties appProperties;

  /** 大域既定の有効期間（{@code app.jwt.expiration}）で発行する。通常のログイン経路はこちらを使う。 */
  public Token issue(String subject, Map<String, Object> claims) {
    return issue(subject, claims, Instant.now().plusMillis(appProperties.getJwtExpiration()));
  }

  /**
   * 期限を明示して発行する。トークンの exp を発行元の記録が持つ期限と同一の値に揃えるための口で、 大域既定とは独立に短命なトークンを作る経路（緊急昇格）が使う。
   *
   * <p>大域既定を短くして代用はできない。{@code app.jwt.expiration} は資格情報の版キャッシュの TTL も兼ねる。
   */
  public Token issue(String subject, Map<String, Object> claims, Instant exp) {
    Instant now = Instant.now();
    JwtClaimsSet claimsSet =
        JwtClaimsSet.builder()
            .issuer(ISSUER_PLATFORM)
            .subject(subject)
            .issuedAt(now)
            .expiresAt(exp)
            .claims(c -> c.putAll(claims))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claimsSet));
    return new Token(jwt.getTokenValue(), exp.toEpochMilli());
  }
}
