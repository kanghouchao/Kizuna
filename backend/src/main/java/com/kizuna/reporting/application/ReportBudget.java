package com.kizuna.reporting.application;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.export.DocumentBudget;

public final class ReportBudget extends DocumentBudget {
  public ReportBudget(AppProperties.OperationalReport settings) {
    super(settings);
  }
}
