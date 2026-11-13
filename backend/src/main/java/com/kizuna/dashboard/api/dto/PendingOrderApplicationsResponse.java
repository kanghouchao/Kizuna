package com.kizuna.dashboard.api.dto;

import com.kizuna.dashboard.application.PendingOrderApplications;
import java.time.LocalDate;

public record PendingOrderApplicationsResponse(LocalDate businessDate, long count) {
  public static PendingOrderApplicationsResponse from(PendingOrderApplications pending) {
    return new PendingOrderApplicationsResponse(pending.businessDate(), pending.count());
  }
}
