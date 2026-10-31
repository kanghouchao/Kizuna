package com.kizuna.auth.infrastructure;

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

/**
 * トークン発行。署名は HS256 に固定し、{@link JwtDecoderConfig} の decoder と揃える。有効期間は種別ごとの設定で決め、大域の {@code
 * app.jwt.expiration} は使わない（資格情報の版キャッシュの TTL を兼ねるため、アクセストークンに合わせて短くできない）。
 */
@Component
@RequiredArgsConstructor
public class JwtIssuer {

  /** トークン発行者 */
  public static final String ISSUER = "Kizuna";

  private final JwtEncoder jwtEncoder;
  private final AppProperties appProperties;

  public IssuedJwt issue(
      TokenType type, String subject, Map<String, Object> claims, String sessionId) {
    Instant now = Instant.now();
    Instant exp = now.plusMillis(type.lifetimeMillis(appProperties));
    JwtClaimsSet claimsSet =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(subject)
            .issuedAt(now)
            .expiresAt(exp)
            .claims(c -> c.putAll(claims))
            .claim(TokenType.CLAIM, type.claimValue())
            .claim(TokenType.SESSION_ID_CLAIM, sessionId)
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claimsSet));
    return new IssuedJwt(jwt.getTokenValue(), exp);
  }
}
