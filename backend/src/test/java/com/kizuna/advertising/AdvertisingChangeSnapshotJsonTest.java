package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.advertising.application.AdvertisingViews.CostView;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingMedium;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

/** 変更履歴の前後の値は保存済みの行から読み戻すので、書き出す JSON の形を既存の行と同じに保つ。 */
@JsonTest
class AdvertisingChangeSnapshotJsonTest {
  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-03T01:02:03.123456Z");
  private static final CostView COST =
      new CostView(
          "c1",
          1L,
          "2026-10",
          AdvertisingCategory.SALES,
          new AdvertisingMedium("m1", "媒体"),
          null,
          "計画",
          null,
          1000,
          4L,
          AT,
          AT);
  private static final String STORED =
      "{\"id\":\"c1\",\"store_id\":1,\"month\":\"2026-10\",\"category\":\"SALES\","
          + "\"medium\":{\"id\":\"m1\",\"name\":\"媒体\"},\"agency_name\":null,\"plan_name\":\"計画\","
          + "\"inquiry_count\":null,\"amount\":1000,\"version\":4,"
          + "\"created_at\":\"2026-11-03T01:02:03.123456Z\",\"updated_at\":\"2026-11-03T01:02:03.123456Z\"}";

  @Autowired JsonMapper mapper;

  @Test
  void writesTheShapeOfTheStoredRows() {
    assertThat(mapper.writeValueAsString(COST)).isEqualTo(STORED);
  }

  @Test
  void readsTheStoredRowsBack() {
    assertThat(mapper.readValue(STORED, CostView.class)).isEqualTo(COST);
  }
}
