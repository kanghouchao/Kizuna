package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.MergePreferences;
import com.kizuna.customer.domain.MergeProfile;
import com.kizuna.shared.exception.ServiceException;
import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

public final class MergeInputDeserializer {
  private MergeInputDeserializer() {}

  private static void requireFields(JsonNode node, List<String> names) {
    if (!node.isObject() || names.stream().anyMatch(name -> !node.has(name)))
      throw new ServiceException("確定資料と優先指定は空欄も含めて全項目を指定してください");
  }

  public static final class Profile extends StdDeserializer<MergeProfile> {
    public Profile() {
      super(MergeProfile.class);
    }

    @Override
    public MergeProfile deserialize(JsonParser parser, DeserializationContext context) {
      var node = context.readTree(parser);
      requireFields(
          node,
          List.of(
              "name",
              "address",
              "building_name",
              "landmark",
              "classification",
              "has_pet",
              "usage_areas",
              "ng_type",
              "ng_content"));
      return context.readTreeAsValue(node, MergeProfile.class);
    }
  }

  public static final class Preferences extends StdDeserializer<MergePreferences> {
    public Preferences() {
      super(MergePreferences.class);
    }

    @Override
    public MergePreferences deserialize(JsonParser parser, DeserializationContext context) {
      var node = context.readTree(parser);
      requireFields(node, List.of("phone", "email", "line"));
      return context.readTreeAsValue(node, MergePreferences.class);
    }
  }
}
