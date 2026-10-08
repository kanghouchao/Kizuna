package com.kizuna.survey.api.dto;

import com.kizuna.survey.domain.SurveyInput;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public final class SurveyDateTimeDeserializer extends StdDeserializer<OffsetDateTime> {
  public SurveyDateTimeDeserializer() {
    super(OffsetDateTime.class);
  }

  @Override
  public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) {
    var node = context.readTree(parser);
    if (!node.isString()) throw SurveyInput.invalid();
    try {
      return OffsetDateTime.parse(node.asString());
    } catch (DateTimeParseException error) {
      throw SurveyInput.invalid();
    }
  }
}
