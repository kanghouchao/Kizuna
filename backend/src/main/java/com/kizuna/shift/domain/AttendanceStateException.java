package com.kizuna.shift.domain;

import com.kizuna.shared.exception.ServiceException;

/** 当日実績への不正な記入・操作を表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class AttendanceStateException extends ServiceException {

  public AttendanceStateException(String message) {
    super(message);
  }
}
