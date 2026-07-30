package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Entity;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * jsonb 属性（{@code @Type(JsonBinaryType.class)}）の Java 直列化可能性を機械検証する。
 *
 * <p>hypersistence-utils の既定 clone 実装（ObjectMapperJsonSerializer）は、dirty checking 用の深いコピーを Java
 * 直列化で作る。要素型が {@link Serializable} でないと、値が空でない限り実行時に NonSerializableObjectException となり書き込みが 500
 * になる。 空コレクションでは走らないため、この欠陥は普通のテストや手動確認をすり抜ける。
 *
 * <p>走査対象は {@code @Entity}（とその継承元）に宣言された属性のみ。現在の jsonb 属性はすべてエンティティ側にあるが、{@code @Embeddable} に置かれた
 * jsonb 属性はこの検証を素通りする。
 */
class JsonbSerializableTests {

  @Test
  @DisplayName("jsonb 属性の要素型がすべて Serializable であること")
  void allJsonbAttributeTypesAreSerializable() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

    List<String> offenders = new ArrayList<>();
    List<String> scanned = new ArrayList<>();
    for (var candidate : scanner.findCandidateComponents("com.kizuna")) {
      Class<?> entity = Class.forName(candidate.getBeanClassName());
      for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
        for (Field field : c.getDeclaredFields()) {
          // org.hibernate.annotations.Type は java.lang.reflect.Type と名前が衝突するため FQCN で参照する。
          org.hibernate.annotations.Type type =
              field.getAnnotation(org.hibernate.annotations.Type.class);
          if (type == null || !JsonBinaryType.class.equals(type.value())) {
            continue;
          }
          String attribute = entity.getSimpleName() + "#" + field.getName();
          scanned.add(attribute);
          collectNonSerializable(field.getGenericType(), attribute, offenders);
        }
      }
    }

    assertThat(scanned).isNotEmpty();
    assertThat(offenders).as("Java 直列化できない型を含む jsonb 属性").isEmpty();
  }

  /**
   * jsonb 属性の型を走査し、Java 直列化できない型を offenders へ積む。
   *
   * <p>{@link Collection} / {@link Map} インターフェース自体は Serializable ではないが、実体は Jackson が生成する ArrayList
   * / HashMap なので違反としない。判定対象はその型引数（要素型）である。
   */
  private static void collectNonSerializable(Type type, String attribute, List<String> offenders) {
    if (type instanceof ParameterizedType parameterized) {
      Class<?> raw = (Class<?>) parameterized.getRawType();
      if (!Collection.class.isAssignableFrom(raw) && !Map.class.isAssignableFrom(raw)) {
        requireSerializable(raw, attribute, offenders);
      }
      for (Type argument : parameterized.getActualTypeArguments()) {
        collectNonSerializable(argument, attribute, offenders);
      }
    } else if (type instanceof Class<?> clazz) {
      // 生の List / Map はここで違反となる（要素型が静的に分からず、判定を素通りさせられない）。
      requireSerializable(clazz, attribute, offenders);
    } else {
      offenders.add(attribute + " -> 型を静的に解決できません: " + type);
    }
  }

  private static void requireSerializable(
      Class<?> clazz, String attribute, List<String> offenders) {
    if (!Serializable.class.isAssignableFrom(clazz)) {
      offenders.add(attribute + " -> " + clazz.getName());
    }
  }
}
