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

  public Token issue(String subject, Map<String, Object> claims) {
    Instant now = Instant.now();
    Instant exp = now.plusMillis(appProperties.getJwtExpiration());
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
