package com.kizuna.auth.infrastructure;

import com.kizuna.shared.config.AppProperties;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * {@link JwtEncoderConfig}・{@link JwtDecoderConfig}・{@link TokenBlacklistService} が共有する HMAC
 * 対称鍵を組み立てる。 {@link SecretKeySpec} 自体は鍵長を検証しないため、HS256（256bit）の要件を満たさない secret はここで bean 生成期（起動時）に
 * fail-fast させ、短い secret のまま起動・ヘルスチェックが通り認証だけ全滅する事態を防ぐ。
 */
final class HmacSecretKeyFactory {

  private static final int MIN_KEY_BYTES = 32;

  private HmacSecretKeyFactory() {}

  static SecretKey create(AppProperties appProperties) {
    byte[] bytes = appProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_KEY_BYTES) {
      throw new IllegalStateException(
          "APP_JWT_SECRET は HS256 の要件で 32 バイト以上必要（現在 " + bytes.length + " バイト）");
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }
}
