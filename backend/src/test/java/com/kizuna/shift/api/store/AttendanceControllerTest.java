package com.kizuna.shift.api.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * 当日実績に物理削除の経路が無いことを構造で固定する。
 *
 * <p>HTTP を叩いて「消えないこと」を見る形は採らない — 配線されていないメソッドは全域ハンドラの兜底で 500 になり、 「削除口が無い」と「削除口が壊れている」を区別できない。ここは
 * handler の集合そのものを数える。
 */
class AttendanceControllerTest {

  @Test
  @DisplayName("DELETE の handler が一つも配線されていないこと（法定保存 — ADR 0014）")
  void declaresNoDeleteHandler() {
    Method[] handlers = AttendanceController.class.getDeclaredMethods();

    assertThat(handlers).as("走査対象が空でないこと").isNotEmpty();
    assertThat(Arrays.stream(handlers).filter(AttendanceControllerTest::mapsDelete).toList())
        .as("誤建の実績は取消標記で外すのであって、行を消す口は持たない")
        .isEmpty();
  }

  private static boolean mapsDelete(Method handler) {
    if (handler.isAnnotationPresent(DeleteMapping.class)) {
      return true;
    }
    RequestMapping mapping = handler.getAnnotation(RequestMapping.class);
    return mapping != null && Arrays.asList(mapping.method()).contains(RequestMethod.DELETE);
  }
}
