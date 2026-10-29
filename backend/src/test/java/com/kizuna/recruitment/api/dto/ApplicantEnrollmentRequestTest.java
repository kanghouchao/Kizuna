package com.kizuna.recruitment.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.api.dto.CastCreateRequest;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** 在籍登録の本文はキャスト直接登録と同じ項目・同じ検証を持ち、二つの作成経路で受け付ける入力がずれないこと。 */
class ApplicantEnrollmentRequestTest {

  @Test
  void mirrorsCastCreateRequestFieldsAndConstraints() {
    Map<String, Field> cast =
        Arrays.stream(CastCreateRequest.class.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .collect(Collectors.toMap(Field::getName, Function.identity()));
    Map<String, RecordComponent> enrollment =
        Arrays.stream(ApplicantEnrollmentRequest.class.getRecordComponents())
            .filter(component -> !component.getName().equals("version"))
            .collect(Collectors.toMap(RecordComponent::getName, Function.identity()));
    assertThat(enrollment.keySet()).containsExactlyInAnyOrderElementsOf(cast.keySet());
    cast.forEach(
        (name, field) -> {
          RecordComponent component = enrollment.get(name);
          assertThat(component.getType()).as(name).isEqualTo(field.getType());
          assertThat(constraints(component.getAccessor().getDeclaringClass(), name))
              .as(name)
              .isEqualTo(constraints(field));
        });
  }

  private static List<String> constraints(Class<?> record, String name) {
    try {
      return constraints(record.getDeclaredField(name));
    } catch (NoSuchFieldException e) {
      throw new AssertionError(e);
    }
  }

  private static List<String> constraints(AnnotatedElement element) {
    return Arrays.stream(element.getAnnotations())
        .filter(a -> a.annotationType().getPackageName().startsWith("jakarta.validation"))
        .map(ApplicantEnrollmentRequestTest::describe)
        .sorted()
        .toList();
  }

  private static String describe(Annotation annotation) {
    return annotation instanceof Pattern pattern
        ? "Pattern(" + pattern.regexp() + ")"
        : annotation.annotationType().getSimpleName();
  }
}
