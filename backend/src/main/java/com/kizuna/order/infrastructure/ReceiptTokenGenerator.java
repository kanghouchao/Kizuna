package com.kizuna.order.infrastructure;

import com.kizuna.shared.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 伝票トークンの生値の生成と、保存・照合に使う鍵付きダイジェストの算出。
 *
 * <p>生値は暗号学的擬似乱数 32 バイト。推測・列挙できないことが所持を証明と認める根拠そのものであり、 桁数を削ると帰属の成立要件そのものが緩む。
 *
 * <p>ダイジェストが鍵付き（HMAC）なのは、データベースだけを手に入れた者が候補を総当たりしても未申領のトークンへ 到達できないようにするため。鍵はアプリケーションの対称秘密（{@code
 * app.jwt.secret}）から用途ラベル付きで 派生させる — 用途ごとに独立した鍵になり、この鍵で JWT を、JWT の鍵でトークンを偽造することはできない。 秘密が 32
 * バイト以上であることは JWT 側の鍵組み立てが起動時に検証しており、満たさない秘密ではそもそも起動しない。
 *
 * <p><b>運用上の帰結</b>: {@code APP_JWT_SECRET} を交換すると既存のダイジェストは二度と一致せず、未申領の伝票トークンは
 * すべて申領不能になる。配り直す口も人手で申領を通す口も無いため、秘密の交換は 未申領分を捨てる決定と同義になる。
 */
@Component
public class ReceiptTokenGenerator {

  private static final String ALGORITHM = "HmacSHA256";

  /** 鍵の用途ラベル。別用途の鍵と衝突させないための定数で、変えると既存のダイジェストが一致しなくなる。 */
  private static final String KEY_PURPOSE = "kizuna:order-receipt-token-digest:v1";

  private static final int TOKEN_BYTES = 32;

  private final SecretKey digestKey;

  private final SecureRandom random = new SecureRandom();

  public ReceiptTokenGenerator(AppProperties appProperties) {
    this.digestKey =
        new SecretKeySpec(
            hmac(
                new SecretKeySpec(
                    appProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM),
                KEY_PURPOSE),
            ALGORITHM);
  }

  /** 発行 1 回分の生値とそのダイジェスト。生値は呼出側が応答へ載せるためだけに存在し、保存してはならない。 */
  public record GeneratedToken(String raw, String digest) {}

  /** 新しい伝票トークンを生成する。同じ生値が二度出ることは実質的に無い。 */
  public GeneratedToken generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    return new GeneratedToken(raw, digest(raw));
  }

  /** 提示された生値のダイジェスト。保存された値との照合はこの写像を通して行う（生値の比較は存在しない）。 */
  public String digest(String raw) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(digestKey, raw));
  }

  private static byte[] hmac(SecretKey key, String message) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      // HmacSHA256 は JCA の必須アルゴリズムで、鍵も自前で組み立てている。ここへ来るのは実装欠陥。
      throw new IllegalStateException("伝票トークンのダイジェストを算出できません", e);
    }
  }
}
