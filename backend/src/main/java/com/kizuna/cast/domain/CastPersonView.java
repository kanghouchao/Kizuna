package com.kizuna.cast.domain;

import java.time.LocalDate;

public interface CastPersonView extends CastPersonSummaryView {
  Long getUserId();

  LocalDate getBirthDate();
}
