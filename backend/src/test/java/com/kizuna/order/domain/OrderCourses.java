package com.kizuna.order.domain;

import java.time.OffsetDateTime;

public final class OrderCourses {
  private OrderCourses() {}

  public static OrderCourse course(String name, int minutes, int price) {
    return new OrderCourse(
        "service",
        "revision",
        1,
        name,
        minutes,
        price,
        0,
        "CURRENT_SETTING",
        OffsetDateTime.parse("2026-09-15T00:00:00Z"));
  }
}
