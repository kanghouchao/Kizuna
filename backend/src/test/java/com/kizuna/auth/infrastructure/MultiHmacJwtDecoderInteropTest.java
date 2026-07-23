package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.config.AppProperties;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * 地基テスト（本票の前提条件）。
 *
 * <p>発行側 {@link JwtUtil} は jjwt の {@code signWith(key)} 単参呼び出しで、鍵長から HS256/384/512 を自動選択する（256bit
 * 未満は不可、256〜383bit=HS256、384〜511bit=HS384、512bit 以上=HS512）。decoder を単一アルゴリズムへ固定すると、運用中の {@code
 * APP_JWT_SECRET} の長さ次第で現網発行済みトークンが全滅しうる。
 *
 * <p>{@link MultiHmacJwtDecoderFactory} が構築する decoder は HS256/384/512 のいずれで署名された トークンも同一 HMAC
 * 鍵で受理できることを、secret 長 32/40/48/64byte のマトリクス（HS256 境界・HS384 ちょうど・HS512
 * のすべてを踏む）で固定する。ここが割れる場合、実装を先へ進めてはならない。
 */
class MultiHmacJwtDecoderInteropTest {

  @ParameterizedTest(name = "secret長{0}byte: jjwt発行トークンを自組 multi-HS decoder が解読できる")
  @ValueSource(ints = {32, 40, 48, 64})
  void jjwtIssuedTokenIsDecodableAcrossSecretLengths(int secretLengthBytes) {
    String secret = randomAsciiSecret(secretLengthBytes);
    JwtUtil jwtUtil = createJwtUtil(secret);

    String token =
        jwtUtil
            .generateToken(
                "interop-user@kizuna.test",
                JwtUtil.ISSUER_PLATFORM,
                Map.of("authorities", List.of("PERM_INTEROP_TEST")))
            .token();

    SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    NimbusJwtDecoder decoder = MultiHmacJwtDecoderFactory.create(key);

    Jwt decoded = decoder.decode(token);

    assertThat(decoded.getSubject()).isEqualTo("interop-user@kizuna.test");
    assertThat(decoded.getClaimAsString("iss")).isEqualTo(JwtUtil.ISSUER_PLATFORM);
    assertThat(decoded.getClaimAsStringList("authorities")).containsExactly("PERM_INTEROP_TEST");
  }

  /** ASCII 文字のみで構成し、文字数=UTF-8 バイト数を保証する（鍵長マトリクスを正確に踏むため）。 */
  private static String randomAsciiSecret(int lengthBytes) {
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    SecureRandom random = new SecureRandom();
    StringBuilder sb = new StringBuilder(lengthBytes);
    for (int i = 0; i < lengthBytes; i++) {
      sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
    }
    return sb.toString();
  }

  private static JwtUtil createJwtUtil(String secret) {
    AppProperties properties = new AppProperties();
    AppProperties.Jwt jwt = new AppProperties.Jwt();
    jwt.setSecret(secret);
    jwt.setExpiration(3_600_000L);
    properties.setJwt(jwt);
    return new JwtUtil(properties);
  }
}
