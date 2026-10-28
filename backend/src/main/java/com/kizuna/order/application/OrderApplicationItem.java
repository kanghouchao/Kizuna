package com.kizuna.order.application;

import com.kizuna.order.domain.OrderApplicationView;

/** 予約申請 1 件の一覧上の姿。{@code expired} は営業日に対する期限切れの判定で、読んだ時点の営業日で決まる。 */
public record OrderApplicationItem(OrderApplicationView view, boolean expired) {}
