package com.kizuna.remuneration.application;

import com.kizuna.shared.exception.ServiceException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.data.domain.PageRequest;

final class RemunerationInput {
  private RemunerationInput() {}

  static PageRequest page(int page, int size) {
    if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
      throw new ServiceException("ページ指定が不正です");
    return PageRequest.of(page, size);
  }

  static YearMonth month(String value) {
    try {
      if (value == null || !value.matches("[0-9]{4}-(0[1-9]|1[0-2])") || value.startsWith("0000"))
        throw new ServiceException("対象月は YYYY-MM 形式で指定してください");
      return YearMonth.parse(value);
    } catch (DateTimeParseException e) {
      throw new ServiceException("対象月が不正です");
    }
  }

  static void person(Long id) {
    if (id == null || id <= 0) throw new ServiceException("キャスト本人の指定が不正です");
  }
}
