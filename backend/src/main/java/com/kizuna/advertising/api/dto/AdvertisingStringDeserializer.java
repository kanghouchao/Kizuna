package com.kizuna.advertising.api.dto;

import com.kizuna.shared.exception.ServiceException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public class AdvertisingStringDeserializer extends StdDeserializer<String> {
  public AdvertisingStringDeserializer() {
    super(String.class);
  }

  @Override
  public String deserialize(JsonParser parser, DeserializationContext context) {
    var node = context.readTree(parser);
    if (!node.isString()) throw new ServiceException("名称・年月・理由は文字列で指定してください");
    return node.asString();
  }

  public static final class Required extends AdvertisingStringDeserializer {
    @Override
    public String getAbsentValue(DeserializationContext context) {
      throw new ServiceException("省略できない項目です。未設定の場合は null を指定してください");
    }
  }
}
