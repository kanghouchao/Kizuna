package com.kizuna.dashboard.application;

import com.kizuna.order.dashboard.OrderDashboardReader;
import com.kizuna.review.dashboard.ReviewDashboardReader;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shift.dashboard.ShiftDashboardReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 店舗のダッシュボードの「やること」に並ぶ未処理の件数。区画ごとの端点がそれぞれ一つを呼ぶ。 */
@Service
@RequiredArgsConstructor
public class PendingWorkService {
  private final BusinessDateService businessDates;
  private final OrderDashboardReader orders;
  private final ShiftDashboardReader shifts;
  private final ReviewDashboardReader reviews;

  public PendingOrderApplications orderApplications(Long storeId) {
    var today = businessDates.currentBusinessDate();
    return new PendingOrderApplications(today, orders.pendingApplications(storeId, today));
  }

  public long shiftRequests(Long storeId) {
    return shifts.pendingRequests(storeId);
  }

  public long reviews(Long storeId) {
    return reviews.pendingReviews(storeId);
  }
}
