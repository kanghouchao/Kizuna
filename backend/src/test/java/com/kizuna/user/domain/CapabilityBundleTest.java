package com.kizuna.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CapabilityBundleTest {

  @Test
  @DisplayName("名称と能力集合を持つ束を構築できる")
  void buildsBundleWithNameAndCapabilities() {
    CapabilityBundle bundle =
        CapabilityBundle.builder()
            .name("店長")
            .capabilities(Set.of(Capability.ORDER_MANAGE, Capability.CAST_INVITE))
            .build();

    assertThat(bundle.getName()).isEqualTo("店長");
    assertThat(bundle.getCapabilities())
        .containsExactlyInAnyOrder(Capability.ORDER_MANAGE, Capability.CAST_INVITE);
  }

  @Test
  @DisplayName("名称が空白だと不変条件違反で例外")
  void blankNameThrows() {
    assertThatThrownBy(
            () ->
                CapabilityBundle.builder()
                    .name("  ")
                    .capabilities(Set.of(Capability.ORDER_MANAGE))
                    .build())
        .isInstanceOf(InvalidCapabilityBundleException.class);
  }

  @Test
  @DisplayName("能力集合が空だと不変条件違反で例外")
  void emptyCapabilitiesThrows() {
    assertThatThrownBy(() -> CapabilityBundle.builder().name("空の束").capabilities(Set.of()).build())
        .isInstanceOf(InvalidCapabilityBundleException.class);
  }

  @Test
  @DisplayName("能力集合が null だと不変条件違反で例外")
  void nullCapabilitiesThrows() {
    assertThatThrownBy(() -> CapabilityBundle.builder().name("null の束").build())
        .isInstanceOf(InvalidCapabilityBundleException.class);
  }
}
