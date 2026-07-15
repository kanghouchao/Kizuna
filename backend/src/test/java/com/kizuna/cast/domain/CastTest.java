package com.kizuna.cast.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CastTest {

  @Test
  @DisplayName("未紐づけの档案に平台身分を紐づけられること")
  void linkPlatformUser_whenUnlinked() {
    Cast cast = Cast.builder().name("テスト").build();

    cast.linkPlatformUser(42L);

    assertThat(cast.getPlatformUserId()).isEqualTo(42L);
  }

  @Test
  @DisplayName("既に紐づき済みの档案には再紐づけできないこと")
  void linkPlatformUser_whenAlreadyLinked_isRejected() {
    Cast cast = Cast.builder().name("テスト").platformUserId(1L).build();

    assertThatThrownBy(() -> cast.linkPlatformUser(2L))
        .isInstanceOf(CastInvitationStateException.class);
    assertThat(cast.getPlatformUserId()).isEqualTo(1L);
  }
}
