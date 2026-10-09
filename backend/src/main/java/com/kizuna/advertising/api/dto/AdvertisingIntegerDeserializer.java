package com.kizuna.advertising.api.dto;

import com.kizuna.shared.exception.ServiceException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public class AdvertisingIntegerDeserializer extends StdDeserializer<Integer> {
  public AdvertisingIntegerDeserializer() {
    super(Integer.class);
  }

  @Override
  public Integer deserialize(JsonParser parser, DeserializationContext context) {
    var n = context.readTree(parser);
    if (!n.isIntegralNumber() || !n.canConvertToInt() || n.asLong() < 0)
      throw new ServiceException("金額・人数・版は範囲内の非負整数で指定してください");
    return n.asInt();
  }

  public static final class Required extends AdvertisingIntegerDeserializer {
    @Override
    public Integer getAbsentValue(DeserializationContext context) {
      throw new ServiceException("省略できない項目です。未設定の場合は null を指定してください");
    }
  }
}
