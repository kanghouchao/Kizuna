package com.kizuna.order.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.order.infrastructure.ReceiptTokenGenerator.GeneratedToken;
import com.kizuna.shared.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReceiptTokenGeneratorTest {

  private static final String SECRET = "receipt-token-test-secret-0123456789";

  private static ReceiptTokenGenerator generatorWithSecret(String secret) {
    AppProperties properties = new AppProperties();
    properties.getJwt().setSecret(secret);
    return new ReceiptTokenGenerator(properties);
  }

  @Test
  @DisplayName("生値は 256 ビットの乱数で、生成のたびに異なること")
  void rawTokensCarryFullEntropyAndNeverRepeat() {
    // 推測・列挙できないことが所持を証明と認める根拠そのもの。桁を削ると帰属の成立要件が緩む
    ReceiptTokenGenerator generator = generatorWithSecret(SECRET);

    Set<String> raws = new HashSet<>();
    IntStream.range(0, 100).forEach(i -> raws.add(generator.generate().raw()));

    assertThat(raws).hasSize(100);
    assertThat(Base64.getUrlDecoder().decode(raws.iterator().next())).hasSize(32);
  }

  @Test
  @DisplayName("保存される値が生値そのものではないこと")
  void theStoredDigestIsNotTheRawToken() {
    GeneratedToken generated = generatorWithSecret(SECRET).generate();

    assertThat(generated.digest()).isNotEqualTo(generated.raw()).doesNotContain(generated.raw());
    assertThat(Base64.getUrlDecoder().decode(generated.digest())).hasSize(32);
  }

  @Test
  @DisplayName("発行のダイジェストが、同じ生値を後から照合したときと一致すること")
  void theIssuedDigestMatchesTheDigestOfThePresentedRawToken() {
    // 申領はダイジェストで照合する。再現しなければ、正しいトークンを持つ本人が誰も申領できない
    ReceiptTokenGenerator generator = generatorWithSecret(SECRET);
    GeneratedToken generated = generator.generate();

    assertThat(generator.digest(generated.raw())).isEqualTo(generated.digest());
    // 鍵が同じなら別のインスタンスでも一致する（起動をまたいで照合できる条件）
    assertThat(generatorWithSecret(SECRET).digest(generated.raw())).isEqualTo(generated.digest());
  }

  @Test
  @DisplayName("鍵が違えば同じ生値でも別のダイジェストになること（鍵付きハッシュであること）")
  void theDigestIsKeyed() {
    // 鍵無しのハッシュなら、データベースだけを手に入れた者が候補を総当たりで照合できてしまう
    GeneratedToken generated = generatorWithSecret(SECRET).generate();

    assertThat(generatorWithSecret("another-secret-0123456789abcdef01").digest(generated.raw()))
        .isNotEqualTo(generated.digest());
  }

  @Test
  @DisplayName("ダイジェストが秘密そのものの HMAC ではないこと（用途ごとに独立した鍵から導かれること）")
  void theDigestKeyIsDerivedForItsOwnPurpose() {
    // 秘密をそのまま鍵に使うと、同じ鍵で JWT と伝票トークンの両方が作れる状態になる
    GeneratedToken generated = generatorWithSecret(SECRET).generate();

    assertThat(generated.digest()).isNotEqualTo(hmacWithRawSecret(SECRET, generated.raw()));
  }

  private static String hmacWithRawSecret(String secret, String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
