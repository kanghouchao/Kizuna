package com.kizuna.shared.storescope;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoreScopeTest {

  @Test
  @DisplayName("authorizes: 含む店舗は true、含まない店舗と null は false")
  void authorizesRespectsSpecificSet() {
    StoreScope scope = new StoreScope(false, Set.of(1L));

    assertThat(scope.authorizes(1L)).isTrue();
    assertThat(scope.authorizes(2L)).isFalse();
    assertThat(scope.authorizes(null)).isFalse();
  }

  @Test
  @DisplayName("authorizes: ALL_STORES は任意店舗で true、ただし null は false")
  void authorizesAllStoresExceptNull() {
    StoreScope scope = new StoreScope(true, Set.of());

    assertThat(scope.authorizes(1L)).isTrue();
    assertThat(scope.authorizes(999L)).isTrue();
    assertThat(scope.authorizes(null)).isFalse();
  }
}
