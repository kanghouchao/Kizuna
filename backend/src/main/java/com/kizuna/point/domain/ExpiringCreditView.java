package com.kizuna.point.domain;

import java.time.LocalDate;

/** 期限前案内が読む加算ロットの最小表現。実体を読み込まないので、同じ取引の書き込みの flush で台帳の行が検査されない。 */
public interface ExpiringCreditView {
  Long getId();

  Long getMemberId();

  int getAmount();

  LocalDate getExpiresOn();
}
