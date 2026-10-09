package com.kizuna.advertising.api.dto;

import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.shared.exception.ServiceException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public final class AdvertisingCategoryDeserializer extends StdDeserializer<AdvertisingCategory> {
  public AdvertisingCategoryDeserializer() {
    super(AdvertisingCategory.class);
  }

  @Override
  public AdvertisingCategory deserialize(JsonParser parser, DeserializationContext context) {
    var node = context.readTree(parser);
    if (node.isString()) {
      if ("SALES".equals(node.asString())) return AdvertisingCategory.SALES;
      if ("RECRUITMENT".equals(node.asString())) return AdvertisingCategory.RECRUITMENT;
    }
    throw new ServiceException("区分は SALES または RECRUITMENT で指定してください");
  }
}
