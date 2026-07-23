package com.kizuna.auth.infrastructure;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * HMAC 対称鍵 1 本で HS256/HS384/HS512 いずれの署名も受理する decoder を組み立てる。
 *
 * <p>発行側（{@link JwtUtil}）は jjwt の {@code signWith(key)} 単参呼び出しで、鍵長から署名アルゴリズムを 自動選択する。decoder
 * を単一アルゴリズムへ固定すると、運用中の secret 長次第で現網発行済みトークンの 検証が全滅しうるため、同一鍵に対して HS 系列すべてを許容する。同一鍵から導出される MAC は
 * アルゴリズムを跨いでも別個の値になるため、複数 HS アルゴリズムを許容しても署名の偽造可能性は増えない （非対称アルゴリズム RS/ES は選択肢に含めないため、鍵種別を跨いだ
 * alg-confusion も起こらない）。
 *
 * <p>クレームの時刻・issuer・ブラックリスト検証は Nimbus 側でなく Spring の {@code OAuth2TokenValidator} 連鎖へ一任する（{@link
 * NimbusJwtDecoder} の各標準ビルダーと同じ作法）。
 */
final class MultiHmacJwtDecoderFactory {

  private static final Set<JWSAlgorithm> ACCEPTED_ALGORITHMS =
      Set.of(JWSAlgorithm.HS256, JWSAlgorithm.HS384, JWSAlgorithm.HS512);

  private MultiHmacJwtDecoderFactory() {}

  static NimbusJwtDecoder create(SecretKey secretKey) {
    JWSKeySelector<SecurityContext> keySelector =
        new JWSVerificationKeySelector<>(ACCEPTED_ALGORITHMS, new ImmutableSecret<>(secretKey));
    ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
    jwtProcessor.setJWSKeySelector(keySelector);
    // 時刻・issuer 等のクレーム検証は Spring の OAuth2TokenValidator 連鎖（skew=0 含む）へ一任するため、
    // Nimbus 既定のクレーム検証（60 秒スキュー等）を無効化する。
    jwtProcessor.setJWTClaimsSetVerifier((claims, context) -> {});
    return new NimbusJwtDecoder(jwtProcessor);
  }
}
