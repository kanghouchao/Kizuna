package com.kizuna.review.api.dto;

import com.kizuna.shared.exception.ServiceException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public final class ReviewVersionDeserializer extends StdDeserializer<Long> {
  public ReviewVersionDeserializer() {
    super(Long.class);
  }

  @Override
  public Long deserialize(JsonParser parser, DeserializationContext context) {
    var node = context.readTree(parser);
    if (!node.isIntegralNumber() || !node.canConvertToLong() || node.asLong() < 0)
      throw new ServiceException("版は非負の整数で指定してください");
    return node.asLong();
  }
}
