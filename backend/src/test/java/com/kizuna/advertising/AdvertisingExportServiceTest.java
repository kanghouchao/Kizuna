package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.advertising.application.AdvertisingExportService;
import com.kizuna.advertising.application.AdvertisingService;
import com.kizuna.advertising.infrastructure.AdvertisingRenderer;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AdvertisingExportServiceTest {
  @Test
  void boundsConcurrentExportsAndReleasesTheSlotAfterFailure() throws Exception {
    var records = mock(AdvertisingService.class);
    var renderer = mock(AdvertisingRenderer.class);
    var service = new AdvertisingExportService(records, renderer, new AppProperties());
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(records.snapshot("2026-09"))
        .thenAnswer(
            invocation -> {
              entered.countDown();
              if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("待機期限");
              throw new ServiceUnavailableException("読取期限");
            });
    try (var pool = Executors.newSingleThreadExecutor()) {
      var first = pool.submit(() -> service.export("2026-09", "csv"));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> service.export("2026-09", "csv"))
          .isInstanceOf(ServiceUnavailableException.class);
      release.countDown();
      assertThatThrownBy(() -> first.get(5, TimeUnit.SECONDS))
          .hasCauseInstanceOf(ServiceUnavailableException.class);
    } finally {
      release.countDown();
    }
    doReturn(null).when(records).snapshot("2026-09");
    when(renderer.render(eq(null), eq("csv"), any())).thenReturn(new byte[] {1});
    assertThat(service.export("2026-09", "csv")).containsExactly((byte) 1);
    assertThatThrownBy(() -> service.export("2026-09", "pdf")).isInstanceOf(ServiceException.class);
  }
}
