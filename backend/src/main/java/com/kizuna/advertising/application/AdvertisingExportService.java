package com.kizuna.advertising.application;

import com.kizuna.advertising.infrastructure.AdvertisingRenderer;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.export.DocumentBudget;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdvertisingExportService {
  private final AdvertisingService service;
  private final AdvertisingRenderer renderer;
  private final AppProperties properties;
  private final Semaphore slot = new Semaphore(1);

  public byte[] export(String month, String format) {
    AdvertisingInput.month(month);
    if (!"csv".equals(format) && !"xlsx".equals(format))
      throw new ServiceException("出力形式はcsvまたはxlsxで指定してください");
    if (!slot.tryAcquire()) throw new ServiceUnavailableException("広告費を出力中です。しばらくして再試行してください");
    try {
      return AdvertisingFailures.run(
          () -> {
            var budget = new DocumentBudget(properties.getAdvertisingCost());
            var snapshot = service.snapshot(month);
            budget.check();
            try {
              return renderer.render(snapshot, format, budget);
            } catch (IOException ex) {
              throw new UncheckedIOException(ex);
            }
          });
    } finally {
      slot.release();
    }
  }
}
