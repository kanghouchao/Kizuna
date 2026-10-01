package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.order.infrastructure.MonthlyPdfRenderer;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceUnavailableException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.QueryTimeoutException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MonthlyPdfServiceTest {
  @Test
  void concurrentRequestIsRejectedAndSlotIsReleasedAfterFailure() throws Exception {
    var renderer = mock(MonthlyPdfRenderer.class);
    when(renderer.render(any(), any())).thenReturn(new byte[] {1});
    var service = new MonthlyPdfService(renderer, new AppProperties());
    var reading = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var first =
          executor.submit(
              () ->
                  service.generate(
                      budget -> {
                        reading.countDown();
                        try {
                          if (!release.await(5, TimeUnit.SECONDS))
                            throw new IllegalStateException();
                        } catch (InterruptedException ex) {
                          Thread.currentThread().interrupt();
                        }
                        throw new IllegalStateException("取得失敗");
                      }));
      try {
        assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> service.generate(budget -> null))
            .isInstanceOf(ServiceUnavailableException.class);
      } finally {
        release.countDown();
      }
      assertThatThrownBy(() -> first.get(5, TimeUnit.SECONDS))
          .hasCauseInstanceOf(IllegalStateException.class);
    }
    assertThat(service.generate(budget -> null)).containsExactly((byte) 1);
  }

  @Test
  void databaseTimeoutsBecomeRetryableAndReleaseSlot() throws Exception {
    var renderer = mock(MonthlyPdfRenderer.class);
    when(renderer.render(any(), any())).thenReturn(new byte[] {1});
    var service = new MonthlyPdfService(renderer, new AppProperties());
    for (var failure :
        List.of(
            new QueryTimeoutException(),
            new PersistenceException(new SQLException("非公開の DB 情報", "57014")))) {
      assertThatThrownBy(
              () ->
                  service.generate(
                      budget -> {
                        throw failure;
                      }))
          .isInstanceOf(ServiceUnavailableException.class)
          .hasMessageNotContaining("非公開");
      assertThat(service.generate(budget -> null)).containsExactly((byte) 1);
    }
    var unexpected = new PersistenceException(new SQLException("故障", "08006"));
    assertThatThrownBy(
            () ->
                service.generate(
                    budget -> {
                      throw unexpected;
                    }))
        .isSameAs(unexpected);
  }

  @Test
  void textBudgetCountsUnicodeAndRejectsOverflow() {
    var settings = new AppProperties.MonthlyPdf();
    settings.setMaxCharacters(2);
    var budget = new MonthlyPdfBudget(settings);
    budget.text("𠮷あ");
    assertThatThrownBy(() -> budget.text("い")).isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  void interruptedWorkCannotReturnSuccess() {
    var budget = new MonthlyPdfBudget(new AppProperties.MonthlyPdf());
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(budget::check).isInstanceOf(ServiceUnavailableException.class);
    } finally {
      Thread.interrupted();
    }
  }
}
