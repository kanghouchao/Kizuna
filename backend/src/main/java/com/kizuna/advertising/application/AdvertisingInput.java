package com.kizuna.advertising.application;

import com.kizuna.shared.exception.ServiceException;
import java.time.YearMonth;

public final class AdvertisingInput {
  private AdvertisingInput() {}

  public static String month(String value) {
    try {
      if (value == null || !value.matches("(?!0000)[0-9]{4}-[0-9]{2}"))
        throw new IllegalArgumentException();
      return YearMonth.parse(value).toString();
    } catch (RuntimeException ex) {
      throw new ServiceException("対象月は西暦1年から9999年の年月で指定してください");
    }
  }
}
