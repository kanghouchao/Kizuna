package com.kizuna.cast.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CastFieldDefinitionTest {

  private CastFieldDefinition definition() {
    return CastFieldDefinition.builder()
        .key("blood_type")
        .label("血液型")
        .displayOrder(3)
        .isPublic(false)
        .build();
  }

  @Test
  @DisplayName("apply は null フィールドを変更しない")
  void apply_ignoresNullFields() {
    CastFieldDefinition d = definition();

    d.apply(new CastFieldDefinitionPatch(null, null, null));

    assertThat(d.getLabel()).isEqualTo("血液型");
    assertThat(d.getDisplayOrder()).isEqualTo(3);
    assertThat(d.getIsPublic()).isFalse();
  }

  @Test
  @DisplayName("apply は label のみ単独更新できる")
  void apply_updatesLabelOnly() {
    CastFieldDefinition d = definition();

    d.apply(new CastFieldDefinitionPatch("血液型（更新）", null, null));

    assertThat(d.getLabel()).isEqualTo("血液型（更新）");
    assertThat(d.getDisplayOrder()).isEqualTo(3);
    assertThat(d.getIsPublic()).isFalse();
  }

  @Test
  @DisplayName("apply は displayOrder のみ単独更新できる")
  void apply_updatesDisplayOrderOnly() {
    CastFieldDefinition d = definition();

    d.apply(new CastFieldDefinitionPatch(null, 7, null));

    assertThat(d.getDisplayOrder()).isEqualTo(7);
    assertThat(d.getLabel()).isEqualTo("血液型");
    assertThat(d.getIsPublic()).isFalse();
  }

  @Test
  @DisplayName("apply は isPublic のみ単独更新できる")
  void apply_updatesIsPublicOnly() {
    CastFieldDefinition d = definition();

    d.apply(new CastFieldDefinitionPatch(null, null, true));

    assertThat(d.getIsPublic()).isTrue();
    assertThat(d.getLabel()).isEqualTo("血液型");
    assertThat(d.getDisplayOrder()).isEqualTo(3);
  }

  @Test
  @DisplayName("key はビルダーで設定され apply では変更されない")
  void key_isImmutableThroughApply() {
    CastFieldDefinition d = definition();

    d.apply(new CastFieldDefinitionPatch("別ラベル", 9, true));

    assertThat(d.getKey()).isEqualTo("blood_type");
  }
}
