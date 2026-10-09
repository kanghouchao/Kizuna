package com.kizuna.remuneration.domain;

public record DailyGuarantee(Status status, Long amount) {
  public enum Status {
    NOT_ELIGIBLE,
    PENDING_ATTENDANCE,
    NOT_CONFIGURED,
    STOPPED,
    CALCULATED
  }

  public static DailyGuarantee calculate(
      GuaranteeState state, Long dailyAmount, AttendanceDuration attendance, long orderAmount) {
    if (state == GuaranteeState.STOPPED) return new DailyGuarantee(Status.STOPPED, 0L);
    if (!attendance.qualifies())
      return attendance.incomplete()
          ? new DailyGuarantee(Status.PENDING_ATTENDANCE, null)
          : new DailyGuarantee(Status.NOT_ELIGIBLE, 0L);
    if (state == null) return new DailyGuarantee(Status.NOT_CONFIGURED, null);
    return new DailyGuarantee(
        Status.CALCULATED,
        Math.max(
            0,
            RemunerationAmounts.require(dailyAmount) - RemunerationAmounts.require(orderAmount)));
  }
}
