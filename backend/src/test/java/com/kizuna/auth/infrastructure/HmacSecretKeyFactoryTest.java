package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.config.AppProperties;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class HmacSecretKeyFactoryTest {

  private static AppProperties appProperties(String secret) {
    AppProperties properties = new AppProperties();
    AppProperties.Jwt jwt = new AppProperties.Jwt();
    jwt.setSecret(secret);
    jwt.setExpiration(3_600_000L);
    properties.setJwt(jwt);
    return properties;
  }

  @Test
  void create_secretAtLeast32Bytes_returnsHmacSha256Key() {
    SecretKey key = HmacSecretKeyFactory.create(appProperties("hmacsecretkeyfactorytestsecret32"));

    assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
  }

  @Test
  void create_secretShorterThan32Bytes_throwsIllegalStateException() {
    assertThatThrownBy(() -> HmacSecretKeyFactory.create(appProperties("too-short-secret")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32");
  }
}
