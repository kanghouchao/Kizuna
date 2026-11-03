package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingCommand;
import com.kizuna.receipt.FingerprintContributions;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdvertisingCommandTest {
  private static final UUID KEY = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private static final AdvertisingCommand.Create CREATE =
      new AdvertisingCommand.Create(
          "2026-10", AdvertisingCategory.SALES, "m1", "代理", "計画", 3, 1000, KEY);
  private static final AdvertisingCommand.Replace REPLACE =
      new AdvertisingCommand.Replace(
          "c1", AdvertisingCategory.SALES, "m1", "代理", "計画", 3, 1000, 4L, "理由", KEY);
  private static final AdvertisingCommand.Delete DELETE =
      new AdvertisingCommand.Delete("c1", 4L, "理由", KEY);
  private static final AdvertisingCommand.Copy COPY =
      new AdvertisingCommand.Copy("2026-10", 1L, 2L, "理由", KEY);

  // 既存の受領行と照合が続くので、移設前の算出が出していた値そのものを固定する。
  @Test
  void fingerprintsMatchTheValuesAlreadyStoredInReceipts() {
    assertThat(CREATE.fingerprint())
        .isEqualTo("74913623b18aeb2ba0a101346dd68f79b17d40e51e0bf266ec72f45fd04928bb");
    assertThat(
            new AdvertisingCommand.Create(
                    "2026-10", AdvertisingCategory.RECRUITMENT, "m1", null, null, null, 0, KEY)
                .fingerprint())
        .isEqualTo("df26b81607eef02c0426ab279af60a46bb9cb0507248fd7e50c441b53a1486c0");
    assertThat(REPLACE.fingerprint())
        .isEqualTo("279c7ab7ab42d9cd55cea3732a227339c175918848908d335cda97b6d9deb706");
    assertThat(DELETE.fingerprint())
        .isEqualTo("d4506a672e72c7b50b3013bd8d679e558bfd8cb8ed3f27867edf47858d6ab93a");
    assertThat(COPY.fingerprint())
        .isEqualTo("e5e560ddf06393fc75569871b3ba3398df58b5bc5c7b604a795e8a64f8e0de95");
  }

  @Test
  void everyItemButTheRequestKeyContributesToTheFingerprint() {
    assertThat(FingerprintContributions.ignored(CREATE, AdvertisingCommand::fingerprint))
        .containsExactly("requestId");
    assertThat(FingerprintContributions.ignored(REPLACE, AdvertisingCommand::fingerprint))
        .containsExactly("requestId");
    assertThat(FingerprintContributions.ignored(DELETE, AdvertisingCommand::fingerprint))
        .containsExactly("requestId");
    assertThat(FingerprintContributions.ignored(COPY, AdvertisingCommand::fingerprint))
        .containsExactly("requestId");
  }
}
