package com.kizuna.shared.web;

import org.springframework.stereotype.Component;
import tools.jackson.core.Version;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.type.LogicalType;

/**
 * 要求本文のスカラーを宣言された JSON 型でだけ受ける。既定は {@code "1"} を整数に、{@code 1} を文字列に黙って変換し、
 * 保存は成功して入力と違う値が入るため。Module API に coercion の口が無いので {@code getOwner()} で builder に届く。 customizer でなく
 * module にしたのは {@code @WebMvcTest} のスライスにも本番と同じ設定を載せるため。
 */
@Component
public class StrictScalarJsonModule extends JacksonModule {

  @Override
  public String getModuleName() {
    return "kizuna-strict-scalars";
  }

  @Override
  public Version version() {
    return Version.unknownVersion();
  }

  @Override
  public void setupModule(SetupContext context) {
    ((MapperBuilder<?, ?>) context.getOwner())
        .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
        .withCoercionConfig(
            LogicalType.Textual,
            c ->
                c.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
        .withCoercionConfig(
            LogicalType.Boolean,
            c ->
                c.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail))
        .withCoercionConfig(
            LogicalType.Integer,
            c ->
                c.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
        .withCoercionConfig(
            LogicalType.Float,
            c ->
                c.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
        .withCoercionConfig(
            LogicalType.Enum,
            c ->
                c.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail));
  }
}
