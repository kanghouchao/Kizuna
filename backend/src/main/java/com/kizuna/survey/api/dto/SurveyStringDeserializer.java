package com.kizuna.survey.api.dto;

import com.kizuna.survey.domain.SurveyInput;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public final class SurveyStringDeserializer extends StdDeserializer<String> {
  public SurveyStringDeserializer() {
    super(String.class);
  }

  @Override
  public String deserialize(JsonParser parser, DeserializationContext context) {
    var node = context.readTree(parser);
    if (!(node.isString())) throw SurveyInput.invalid();
    return node.asString();
  }
}
