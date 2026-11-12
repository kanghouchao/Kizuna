package com.kizuna.dashboard.application;

import com.kizuna.order.dashboard.OrderDashboardReader;
import com.kizuna.settings.application.BusinessDateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TodayOrdersService {
  private final BusinessDateService businessDates;
  private final OrderDashboardReader orders;

  public TodayOrders read(Long storeId) {
    var today = businessDates.currentBusinessDate();
    return new TodayOrders(today, orders.statusCounts(storeId, today));
  }
}
