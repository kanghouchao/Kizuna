package com.kizuna.receipt;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.receipt.once.Fingerprint;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class FingerprintTest {
  @Test
  void lengthPrefixesKeepShiftedBoundariesNullsAndEmptyValuesApart() {
    assertThat(Fingerprint.of("ab", "c")).isNotEqualTo(Fingerprint.of("a", "bc"));
    assertThat(Fingerprint.of((Object) null)).isNotEqualTo(Fingerprint.of(""));
    assertThat(Fingerprint.of(List.of("a", "b"), "c"))
        .isNotEqualTo(Fingerprint.of(List.of("a"), "b", "c"));
    assertThat(Fingerprint.of(Arrays.asList("値", null, 1L)))
        .isEqualTo(Fingerprint.of(Arrays.asList("値", null, 1L)))
        .hasSize(64);
  }
}
