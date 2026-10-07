package com.kizuna.survey.api.dto;

import com.kizuna.survey.domain.SurveyInput;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public final class SurveyVersionDeserializer extends StdDeserializer<Long> {
  public SurveyVersionDeserializer() {
    super(Long.class);
  }

  @Override
  public Long deserialize(JsonParser parser, DeserializationContext context) {
    var node = context.readTree(parser);
    if (!(node.isIntegralNumber()
        && node.canConvertToLong()
        && node.asLong() >= 0
        && node.asLong() <= Integer.MAX_VALUE)) throw SurveyInput.invalid();
    return node.asLong();
  }
}
