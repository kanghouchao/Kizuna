package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.advertising.api.dto.AdvertisingMediaSummaryResponse;
import com.kizuna.advertising.application.AdvertisingMediaRenderer;
import com.kizuna.advertising.application.AdvertisingMediaService.Snapshot;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingMediaReport;
import com.kizuna.advertising.domain.AdvertisingMediaReport.Entry;
import com.kizuna.advertising.domain.AdvertisingMedium;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.export.DocumentBudget;
import com.kizuna.shared.export.ExportCells;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class AdvertisingMediaRendererTest {
  AdvertisingMediaRenderer renderer = new AdvertisingMediaRenderer();

  Snapshot snapshot(List<Entry> entries) {
    return new Snapshot(
        1,
        "2026-10",
        3,
        OffsetDateTime.parse("2026-10-09T12:00:00Z"),
        AdvertisingMediaReport.aggregate(entries));
  }

  /** id は表示されない数字列にして、出力の媒体列が現在名であることを区別する。 */
  static AdvertisingMedium medium(String name) {
    return new AdvertisingMedium("9" + name, name);
  }

  DocumentBudget budget() {
    return new DocumentBudget(new AppProperties.OperationalReport());
  }

  @Test
  void exportIncludesAllGroupsBeyondTheDisplayedPage() throws Exception {
    var entries =
        IntStream.range(0, 2001)
            .mapToObj(
                i ->
                    new Entry(
                        AdvertisingCategory.SALES,
                        medium(String.format("%05d", i)),
                        100,
                        i == 0 ? null : 0L))
            .toList();
    var report = snapshot(entries);
    var page = AdvertisingMediaSummaryResponse.of(report, PageRequest.of(1, 20));
    assertThat(page.rows().getContent()).hasSize(20);
    assertThat(page.rows().getTotalElements()).isEqualTo(2001);
    assertThat(page.recordedTotalAmount()).isEqualTo(200100);
    assertThat(
            AdvertisingMediaSummaryResponse.of(report, PageRequest.of(Integer.MAX_VALUE, 100))
                .rows())
        .isEmpty();
    String csv = new String(renderer.render(report, "csv", budget()), StandardCharsets.UTF_8);
    assertThat(csv.split("\r\n")).hasSize(2005);
    assertThat(ExportCells.letterRuns(csv)).containsExactly("ID");
    try (var book =
        new XSSFWorkbook(new ByteArrayInputStream(renderer.render(report, "xlsx", budget())))) {
      assertThat(book.getSheet("媒体別集計").getLastRowNum()).isEqualTo(2001);
    }
  }
}
