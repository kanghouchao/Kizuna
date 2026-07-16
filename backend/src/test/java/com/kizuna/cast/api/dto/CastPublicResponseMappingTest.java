package com.kizuna.cast.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastFieldDefinition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/** CastMapper.toPublicResponse の可視性フィルタ（公開のみ・非空のみ・表示順ソート）を検証する。 */
class CastPublicResponseMappingTest {

  private final CastMapper mapper = Mappers.getMapper(CastMapper.class);

  private CastFieldDefinition definition(String key, String label, int order, boolean isPublic) {
    return CastFieldDefinition.builder()
        .key(key)
        .label(label)
        .displayOrder(order)
        .isPublic(isPublic)
        .build();
  }

  private Cast castWith(Map<String, String> customFields) {
    return Cast.builder().name("テスト").customFields(customFields).build();
  }

  @Test
  void excludesNonPublicDefinitions() {
    Cast cast = castWith(Map.of("blood_type", "A型", "secret", "内緒"));
    List<CastFieldDefinition> definitions =
        List.of(definition("blood_type", "血液型", 0, true), definition("secret", "秘密", 1, false));

    CastPublicResponse response = mapper.toPublicResponse(cast, definitions);

    assertThat(response.getCustomFields())
        .extracting(CastCustomFieldView::key)
        .containsExactly("blood_type");
  }

  @Test
  void excludesEmptyAndMissingValues() {
    Map<String, String> values = new HashMap<>();
    values.put("blood_type", "A型");
    values.put("hobby", "");
    // "height" は定義があるが値なし
    Cast cast = castWith(values);
    List<CastFieldDefinition> definitions =
        List.of(
            definition("blood_type", "血液型", 0, true),
            definition("hobby", "趣味", 1, true),
            definition("height", "身長", 2, true));

    CastPublicResponse response = mapper.toPublicResponse(cast, definitions);

    assertThat(response.getCustomFields())
        .extracting(CastCustomFieldView::key)
        .containsExactly("blood_type");
  }

  @Test
  void sortsByDisplayOrderAscending() {
    Cast cast = castWith(Map.of("a", "1", "b", "2", "c", "3"));
    List<CastFieldDefinition> definitions =
        List.of(
            definition("c", "C", 2, true),
            definition("a", "A", 0, true),
            definition("b", "B", 1, true));

    CastPublicResponse response = mapper.toPublicResponse(cast, definitions);

    assertThat(response.getCustomFields())
        .extracting(
            CastCustomFieldView::key, CastCustomFieldView::label, CastCustomFieldView::value)
        .containsExactly(tuple("a", "A", "1"), tuple("b", "B", "2"), tuple("c", "C", "3"));
  }

  @Test
  void nullCustomFieldsYieldsEmptyList() {
    Cast cast = castWith(null);
    List<CastFieldDefinition> definitions = List.of(definition("blood_type", "血液型", 0, true));

    CastPublicResponse response = mapper.toPublicResponse(cast, definitions);

    assertThat(response.getCustomFields()).isEmpty();
  }
}
