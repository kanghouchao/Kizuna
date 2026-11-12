package com.kizuna.dashboard.application;

import com.kizuna.order.dashboard.OrderStatusCounts;
import java.time.LocalDate;

public record TodayOrders(LocalDate businessDate, OrderStatusCounts counts) {}
