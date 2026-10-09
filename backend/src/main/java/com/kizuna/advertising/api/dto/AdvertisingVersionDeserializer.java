package com.kizuna.advertising.api.dto;

import com.kizuna.shared.exception.ServiceException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public final class AdvertisingVersionDeserializer extends StdDeserializer<Long> {
  public AdvertisingVersionDeserializer() {
    super(Long.class);
  }

  @Override
  public Long deserialize(JsonParser parser, DeserializationContext context) {
    var n = context.readTree(parser);
    if (!n.isIntegralNumber() || !n.canConvertToLong() || n.asLong() < 0)
      throw new ServiceException("金額・人数・版は範囲内の非負整数で指定してください");
    return n.asLong();
  }
}
