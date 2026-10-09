package com.kizuna.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.advertising.reporting.AdvertisingReportFacts;
import com.kizuna.order.reporting.OperationalFacts;
import com.kizuna.reporting.api.dto.OperationalReportResponse;
import com.kizuna.reporting.application.OperationalReportService;
import com.kizuna.reporting.application.ReportSnapshot;
import com.kizuna.reporting.domain.OperationalReport;
import com.kizuna.reporting.domain.ReportCriteria;
import com.kizuna.reporting.infrastructure.ReportRenderer;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceUnavailableException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.poi.util.TempFile;
import org.apache.poi.util.TempFileCreationStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdvertisingReportResourceTest {
  final AppProperties properties = new AppProperties();
  final ReportSnapshot snapshot = mock(ReportSnapshot.class);
  final OperationalReportService service =
      new OperationalReportService(snapshot, new ReportRenderer(), properties);

  @Test
  void allAdvertisingProofRowsConsumeSharedCharacterBudgetAndFailedViewReleasesSlot() {
    var costs = costs(2);
    var facts =
        new OperationalFacts(
            OffsetDateTime.now(),
            List.of(new OperationalFacts.Store(1L, "店舗名")),
            List.of(
                new OperationalFacts.Order(
                    "長い受注識別子", 1L, LocalDate.of(2026, 9, 1), 1, false, 100, 20)));
    var report =
        OperationalReport.aggregate(
            ReportCriteria.parse("2026-09-01", "2026-09-30", "month"), facts, null, costs);
    when(snapshot.store(any(), anyBoolean(), anyBoolean())).thenReturn(report);
    int costText = costs.costs().stream().mapToInt(cost -> cost.toString().length()).sum();
    properties.getOperationalReport().setMaxCharacters(costText);
    assertThatThrownBy(this::view).isInstanceOf(ServiceUnavailableException.class);
    properties.getOperationalReport().setMaxCharacters(costText + 100);
    assertThat(view().advertising().entryCount()).isEqualTo(2L);
    properties.getOperationalReport().setMaxOrders(1);
    assertThatThrownBy(this::view).isInstanceOf(ServiceUnavailableException.class);
    properties.getOperationalReport().setMaxOrders(2);
    assertThat(view().advertising().entryCount()).isEqualTo(2L);
  }

  @Test
  void duplicateGenerationIsRejectedWhilePriorSnapshotIsRunningAndSlotRecovers() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(snapshot.store(any(), anyBoolean(), anyBoolean()))
        .thenAnswer(
            invocation -> {
              entered.countDown();
              if (!release.await(5, TimeUnit.SECONDS))
                throw new IllegalStateException("待機解除されませんでした");
              return report(1);
            });
    try (var executor = Executors.newSingleThreadExecutor()) {
      var first = executor.submit(this::view);
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(
                () ->
                    service.export(
                        false, null, "2026-09-01", "2026-09-30", "month", "csv", false, true))
            .isInstanceOf(ServiceUnavailableException.class);
      } finally {
        release.countDown();
      }
      assertThat(first.get(5, TimeUnit.SECONDS).advertising().entryCount()).isEqualTo(1L);
    }
    assertThat(view().advertising().entryCount()).isEqualTo(1L);
  }

  @Test
  void advertisingXlsxDeletesTemporaryFilesAndRecoversAfterCharacterAndByteFailures(
      @TempDir Path directory) throws Exception {
    var report = report(2001);
    when(snapshot.store(any(), anyBoolean(), anyBoolean())).thenReturn(report);
    var created = new ArrayList<Path>();
    var strategy =
        new TempFileCreationStrategy() {
          @Override
          public File createTempFile(String prefix, String suffix) throws IOException {
            var path = Files.createTempFile(directory, prefix, suffix);
            created.add(path);
            return path.toFile();
          }

          @Override
          public File createTempDirectory(String prefix) throws IOException {
            return Files.createTempDirectory(directory, prefix).toFile();
          }
        };
    int inputCharacters =
        report.advertisingFacts().costs().stream().mapToInt(cost -> cost.toString().length()).sum();
    for (String failure : List.of("characters", "bytes", "success")) {
      created.clear();
      properties
          .getOperationalReport()
          .setMaxCharacters(failure.equals("characters") ? inputCharacters + 1500 : 4_000_000);
      properties
          .getOperationalReport()
          .setMaxBytes(failure.equals("bytes") ? 1 : 16L * 1024 * 1024);
      TempFile.withStrategy(
          strategy,
          () -> {
            if (failure.equals("success"))
              assertThatCode(() -> assertThat(export()).isNotEmpty()).doesNotThrowAnyException();
            else assertThatThrownBy(this::export).isInstanceOf(ServiceUnavailableException.class);
            return null;
          });
      assertThat(created)
          .as(failure)
          .anyMatch(path -> path.getFileName().toString().startsWith("poi-sxssf-sheet"));
      assertThat(created).allSatisfy(path -> assertThat(path).doesNotExist());
      assertThat(directory).isEmptyDirectory();
    }
  }

  private OperationalReportResponse view() {
    return service.view(false, null, "2026-09-01", "2026-09-30", "month", 0, 1, false, true);
  }

  private byte[] export() throws IOException {
    return service.export(false, null, "2026-09-01", "2026-09-30", "month", "xlsx", false, true);
  }

  private OperationalReport report(int count) {
    return OperationalReport.aggregate(
        ReportCriteria.parse("2026-09-01", "2026-09-30", "month"),
        new OperationalFacts(
            OffsetDateTime.now(), List.of(new OperationalFacts.Store(1L, "店舗")), List.of()),
        null,
        costs(count));
  }

  private AdvertisingReportFacts costs(int count) {
    return new AdvertisingReportFacts(
        IntStream.range(0, count)
            .mapToObj(
                i ->
                    new AdvertisingReportFacts.Cost(
                        1L, String.format("cost%04d", i), 1, "2026-09", "SALES", 1))
            .toList());
  }
}
