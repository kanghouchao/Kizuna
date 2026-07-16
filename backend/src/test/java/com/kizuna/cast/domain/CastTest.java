package com.kizuna.cast.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CastTest {

  private CastPatch customFieldsPatch(Map<String, String> customFields) {
    return new CastPatch(null, null, null, null, null, null, null, null, null, null, customFields);
  }

  @Test
  @DisplayName("apply の customFields はマージではなく全置換される")
  void apply_customFieldsFullReplace() {
    Cast cast = Cast.builder().name("テスト").customFields(Map.of("a", "1")).build();

    cast.apply(customFieldsPatch(Map.of("b", "2")));

    assertThat(cast.getCustomFields()).containsExactlyInAnyOrderEntriesOf(Map.of("b", "2"));
  }

  @Test
  @DisplayName("apply の customFields が null なら既存値を変更しない")
  void apply_customFieldsNullKeepsExisting() {
    Cast cast = Cast.builder().name("テスト").customFields(Map.of("a", "1")).build();

    cast.apply(customFieldsPatch(null));

    assertThat(cast.getCustomFields()).containsExactlyInAnyOrderEntriesOf(Map.of("a", "1"));
  }

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
