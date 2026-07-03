package com.kizuna.order.domain;

import com.kizuna.shared.exception.ServiceException;

/** 注文ステータスの不正な遷移を表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class IllegalOrderStateTransitionException extends ServiceException {

  public IllegalOrderStateTransitionException(OrderStatus from, OrderStatus to) {
    super(String.format("注文ステータスを %s から %s へ遷移できません", from, to));
  }
}
