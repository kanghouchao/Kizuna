package com.kizuna.survey.api.dto;

import com.kizuna.survey.domain.SurveyInput;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public final class SurveyBooleanDeserializer extends StdDeserializer<Boolean> {
  public SurveyBooleanDeserializer() {
    super(Boolean.class);
  }

  @Override
  public Boolean deserialize(JsonParser parser, DeserializationContext context) {
    var node = context.readTree(parser);
    if (!(node.isBoolean())) throw SurveyInput.invalid();
    return node.asBoolean();
  }
}
