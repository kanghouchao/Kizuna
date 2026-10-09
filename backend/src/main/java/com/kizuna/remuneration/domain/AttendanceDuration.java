package com.kizuna.remuneration.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record AttendanceDuration(Duration closedDuration, boolean incomplete) {
  public record Interval(LocalDateTime start, LocalDateTime end) {}

  public static AttendanceDuration of(List<Interval> intervals) {
    var sorted =
        intervals.stream()
            .filter(i -> i.end() != null)
            .sorted(Comparator.comparing(Interval::start))
            .toList();
    Duration total = Duration.ZERO;
    LocalDateTime start = null;
    LocalDateTime end = null;
    for (var interval : sorted) {
      if (start == null) {
        start = interval.start();
        end = interval.end();
      } else if (!interval.start().isAfter(end)) {
        if (interval.end().isAfter(end)) end = interval.end();
      } else {
        total = total.plus(Duration.between(start, end));
        start = interval.start();
        end = interval.end();
      }
    }
    if (start != null) total = total.plus(Duration.between(start, end));
    return new AttendanceDuration(total, intervals.stream().anyMatch(i -> i.end() == null));
  }

  public boolean qualifies() {
    return closedDuration.compareTo(Duration.ofHours(4)) >= 0;
  }
}
