package com.kizuna.receipt;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** 命令の記録項目を一つずつ別の値に替え、指紋が変わらなかった項目名を返す。項目を足して指紋に入れ忘れても、ここで名前が出る。 */
public final class FingerprintContributions {
  private FingerprintContributions() {}

  public static <T extends Record> List<String> ignored(T command, Function<T, String> print) {
    var base = print.apply(command);
    var components = command.getClass().getRecordComponents();
    var ignored = new ArrayList<String>();
    for (var changed : components) {
      var args = new Object[components.length];
      for (int i = 0; i < components.length; i++) {
        var value = read(components[i], command);
        args[i] = components[i] == changed ? other(changed.getType(), value) : value;
      }
      if (print.apply(construct(command, components, args)).equals(base))
        ignored.add(changed.getName());
    }
    return ignored;
  }

  private static Object read(RecordComponent component, Record command) {
    try {
      return component.getAccessor().invoke(command);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T construct(T command, RecordComponent[] components, Object[] args) {
    try {
      var types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
      var constructor = command.getClass().getDeclaredConstructor(types);
      constructor.setAccessible(true);
      return (T) constructor.newInstance(args);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Object other(Class<?> type, Object value) {
    if (type == String.class) return value + "別";
    if (type == Long.class || type == long.class) return (value == null ? 0L : (Long) value) + 1;
    if (type == Integer.class) return (value == null ? 0 : (Integer) value) + 1;
    if (type == LocalDate.class) return ((LocalDate) value).plusDays(1);
    if (type == UUID.class) return UUID.randomUUID();
    if (type.isEnum())
      return Arrays.stream(type.getEnumConstants())
          .filter(c -> c != value)
          .findFirst()
          .orElseThrow();
    throw new IllegalArgumentException("替える値を決められない型: " + type);
  }
}
