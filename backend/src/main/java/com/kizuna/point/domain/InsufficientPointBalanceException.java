package com.kizuna.point.domain;

import com.kizuna.shared.exception.ServiceException;

/**
 * 消費しようとした量に対してポイント残高が足りないことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。
 *
 * <p>文言に残高を載せるのは、呼出側が「いくらまでなら通るか」を再照会せずに利用者へ提示できるようにするため。
 */
public class InsufficientPointBalanceException extends ServiceException {

  public InsufficientPointBalanceException(int balance) {
    super("ポイント残高が不足しています（残高: " + balance + "）");
  }
}
