package com.kizuna.remuneration.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.remuneration.application.RemunerationViews.BonusView;
import com.kizuna.remuneration.application.RemunerationViews.GuaranteeView;
import com.kizuna.remuneration.domain.GuaranteeState;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

/** 変更履歴の前後の値は保存済みの行と並べて読まれるので、書き出す JSON の形を既存の行と同じに保つ。 */
@JsonTest
class RemunerationChangeSnapshotJsonTest {
  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-03T01:02:03.123456Z");
  private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

  @Autowired JsonMapper mapper;

  @Test
  void writesTheShapeOfTheStoredRows() {
    assertThat(
            mapper.writeValueAsString(
                new GuaranteeView("g1", 7L, DAY, GuaranteeState.ACTIVE, null, "理由", null, AT, AT)))
        .isEqualTo(
            "{\"id\":\"g1\",\"person_id\":7,\"effective_from\":\"2026-10-01\",\"state\":\"ACTIVE\","
                + "\"reason\":\"理由\",\"created_at\":\"2026-11-03T01:02:03.123456Z\","
                + "\"updated_at\":\"2026-11-03T01:02:03.123456Z\"}");
    assertThat(
            mapper.writeValueAsString(
                new BonusView("b1", 7L, DAY, 5000L, 5000L, "理由", 2L, null, AT, AT)))
        .isEqualTo(
            "{\"id\":\"b1\",\"person_id\":7,\"award_date\":\"2026-10-01\",\"amount\":5000,"
                + "\"effective_amount\":5000,\"reason\":\"理由\",\"version\":2,"
                + "\"created_at\":\"2026-11-03T01:02:03.123456Z\","
                + "\"updated_at\":\"2026-11-03T01:02:03.123456Z\"}");
  }
}
