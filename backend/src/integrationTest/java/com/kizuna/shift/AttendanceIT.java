package com.kizuna.shift;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.settings.api.dto.SystemConfigUpdateRequest;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shift.domain.Attendance;
import com.kizuna.shift.domain.AttendanceCorrection;
import com.kizuna.shift.domain.AttendanceCorrectionRepository;
import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 当日実績の記録・訂正・取消と、それを支える二本の部分一意索引を本物の PostgreSQL で確かめる統合テスト。
 *
 * <p>日付変更時刻を 05:00 に倒して走る。既定の 00:00 では営業日が暦日と一致するため、「実開始時刻から営業日を判定した」 のか「日付部分をそのまま写した」のかを区別できない。
 *
 * <p>一意性は取消済みを数えない部分索引で表しており、この定義は「取消 → 再記録」が自らの唯一性に阻まれないための ものである（ADR 0014）。409 の断言では応答文言まで見る —
 * 全域ハンドラが一意違反を汎用文言の 409 で兜底するので、 status だけでは写像が命中したのか素通りしたのかが分からない。
 */
class AttendanceIT extends CrossStoreTestSupport {

  private static final String DATE_CHANGE_TIME_KEY = "business_date_change_time";

  /** 一意違反の全域兜底の文言。写像が命中していれば決してこれにはならない。 */
  private static final String GENERIC_UNIQUE_VIOLATION = "既に登録されている値と重複しています";

  /** 日付変更時刻 05:00 より前なので、この瞬間の営業日は前の暦日である。 */
  private static final LocalDateTime LATE_NIGHT_START = LocalDateTime.of(2026, 8, 19, 2, 30);

  private static final LocalDate LATE_NIGHT_BUSINESS_DATE = LocalDate.of(2026, 8, 18);

  @Autowired private ShiftRepository shiftRepository;
  @Autowired private AttendanceRepository attendanceRepository;
  @Autowired private AttendanceCorrectionRepository attendanceCorrectionRepository;
  @Autowired private SystemConfigService systemConfigService;

  private String castId;

  @BeforeEach
  void setDateChangeTimeAndCreateCast() {
    setDateChangeTime("05:00");
    castId = createCast("実績_" + UUID.randomUUID());
  }

  @AfterEach
  void restoreDateChangeTime() {
    setDateChangeTime("00:00");
  }

  @Test
  @DisplayName("飛び込み（シフトなし）が一等記録として成立し、営業日が実開始時刻から判定されること")
  void walkInAttendanceIsFirstClassAndDerivesItsBusinessDate() {
    ResponseEntity<JsonNode> lateNight = record(castId, null, LATE_NIGHT_START, null);

    assertThat(lateNight.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(lateNight.getBody().path("business_date").asString())
        .as("日付変更時刻 05:00 より前なので前の暦日へ帰属すること")
        .isEqualTo(LATE_NIGHT_BUSINESS_DATE.toString());
    assertThat(lateNight.getBody().has("shift_id")).as("飛び込みはシフト参照を持たないこと").isFalse();
    assertThat(lateNight.getBody().has("actual_end_at")).as("実終了の未記入が許されること").isFalse();

    // 正向対照: 同じ暦日でも日付変更時刻を過ぎていれば当の暦日へ帰属する。
    String otherCastId = createCast("実績_日中_" + UUID.randomUUID());
    ResponseEntity<JsonNode> morning =
        record(otherCastId, null, LATE_NIGHT_START.withHour(6), null);
    assertThat(morning.getBody().path("business_date").asString()).isEqualTo("2026-08-19");
  }

  @Test
  @DisplayName("シフト紐づきの実績が営業日をシフトの勤務日から継承すること")
  void shiftLinkedAttendanceInheritsWorkDate() {
    String shiftId = seedConfirmedShift(castId, LocalDate.of(2026, 8, 18), LocalTime.of(22, 0));

    // 実開始 8/19 06:00 は単独で判定すれば 8/19 側。継承が勝つことがここで分かれる。
    ResponseEntity<JsonNode> created =
        record(castId, shiftId, LocalDateTime.of(2026, 8, 19, 6, 0), null);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().path("business_date").asString()).isEqualTo("2026-08-18");
    assertThat(created.getBody().path("shift_id").asString()).isEqualTo(shiftId);
  }

  @Test
  @DisplayName("同一キャスト・同一営業日の未取消 2 行目が 409 になり、取消のあとは再記録できること")
  void secondActiveRowForTheSameCastAndBusinessDateConflicts() {
    ResponseEntity<JsonNode> first = record(castId, null, LATE_NIGHT_START, null);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<JsonNode> duplicate = record(castId, null, LATE_NIGHT_START.plusHours(1), null);
    assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(duplicate.getBody().path("error").asString())
        .as("兜底の汎用文言ではなく、当たった索引の写像が命中していること")
        .isNotEqualTo(GENERIC_UNIQUE_VIOLATION)
        .contains("営業日");

    assertThat(cancel(first.getBody().path("id").asString()).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<JsonNode> reRecorded = record(castId, null, LATE_NIGHT_START.plusHours(1), null);
    assertThat(reRecorded.getStatusCode()).as("取消済みは一意性に数えないこと").isEqualTo(HttpStatus.CREATED);
  }

  @Test
  @DisplayName("同一シフトの未取消 2 行目が 409 になること")
  void secondActiveRowForTheSameShiftConflicts() {
    String shiftId = seedConfirmedShift(castId, LocalDate.of(2026, 8, 18), LocalTime.of(22, 0));
    assertThat(record(castId, shiftId, LATE_NIGHT_START, null).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);

    ResponseEntity<JsonNode> duplicate = record(castId, shiftId, LATE_NIGHT_START, null);

    assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(duplicate.getBody().path("error").asString())
        .as("兜底の汎用文言ではなく、当たった索引の写像が命中していること")
        .isNotEqualTo(GENERIC_UNIQUE_VIOLATION);
  }

  @Test
  @DisplayName("実終了が実開始以前の記録・訂正が 400 になり、実終了なしは通ること")
  void endNotAfterStartIsRejectedOnBothWriteSurfaces() {
    assertThat(record(castId, null, LATE_NIGHT_START, LATE_NIGHT_START).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<JsonNode> created = record(castId, null, LATE_NIGHT_START, null);
    assertThat(created.getStatusCode()).as("実終了なしは通ること").isEqualTo(HttpStatus.CREATED);

    ResponseEntity<JsonNode> corrected =
        correct(
            created.getBody().path("id").asString(),
            LATE_NIGHT_BUSINESS_DATE,
            LATE_NIGHT_START,
            LATE_NIGHT_START.minusMinutes(1));
    assertThat(corrected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("訂正が就地更新と同一トランザクションで編集前のスナップショットを残すこと")
  void correctionLeavesThePreEditSnapshot() {
    String attendanceId =
        record(castId, null, LATE_NIGHT_START, null).getBody().path("id").asString();
    LocalDate correctedBusinessDate = LATE_NIGHT_BUSINESS_DATE.minusDays(1);

    ResponseEntity<JsonNode> corrected =
        correct(
            attendanceId,
            correctedBusinessDate,
            LATE_NIGHT_START.minusHours(3),
            LATE_NIGHT_START.plusHours(2));

    assertThat(corrected.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(corrected.getBody().path("business_date").asString())
        .as("日付変更時刻の変更が不遡及であるため、誤帰属はここで直せること")
        .isEqualTo(correctedBusinessDate.toString());

    List<AttendanceCorrection> history =
        attendanceCorrectionRepository.findAll().stream()
            .filter(row -> attendanceId.equals(row.getAttendanceId()))
            .toList();
    assertThat(history).hasSize(1);
    assertThat(history.getFirst().getBusinessDate())
        .as("履歴が持つのは編集後ではなく編集前の姿であること")
        .isEqualTo(LATE_NIGHT_BUSINESS_DATE);
    assertThat(history.getFirst().getActualStartAt()).isEqualTo(LATE_NIGHT_START);
    assertThat(history.getFirst().getCastId()).isEqualTo(castId);
    assertThat(history.getFirst().getCorrectedBy()).isNotNull();
    assertThat(history.getFirst().getCorrectedAt()).isNotNull();
  }

  @Test
  @DisplayName("（キャスト・店舗・営業日）で照会でき、取消済みが現れないこと")
  void lookupByCastAndBusinessDateExcludesCancelled() {
    String attendanceId =
        record(castId, null, LATE_NIGHT_START, null).getBody().path("id").asString();
    String otherCastId = createCast("実績_他キャスト_" + UUID.randomUUID());
    String otherAttendanceId =
        record(otherCastId, null, LATE_NIGHT_START, null).getBody().path("id").asString();

    // 同じ営業日は同クラスの他のテストも使うので、件数ではなく両方の在否で見る。
    assertThat(idsOf(list(LATE_NIGHT_BUSINESS_DATE, null)))
        .as("店舗・営業日で引けば両方見えること")
        .contains(attendanceId, otherAttendanceId);
    assertThat(idsOf(list(LATE_NIGHT_BUSINESS_DATE, castId)))
        .as("キャストで絞れること")
        .containsExactly(attendanceId);
    assertThat(idsOf(list(LATE_NIGHT_BUSINESS_DATE.minusDays(1), castId)))
        .as("別の営業日には現れないこと")
        .isEmpty();

    cancel(attendanceId);

    assertThat(idsOf(list(LATE_NIGHT_BUSINESS_DATE, castId))).as("取消済みは照会から外れること").isEmpty();
    assertThat(idsOf(list(LATE_NIGHT_BUSINESS_DATE, null)))
        .as("取消は店舗全体の照会からも外れ、他キャストの行は残ること")
        .doesNotContain(attendanceId)
        .contains(otherAttendanceId);
  }

  @Test
  @DisplayName("取消が行を消さず標記だけを付けること")
  void cancellationIsLogicalAndLeavesTheRow() {
    String attendanceId =
        record(castId, null, LATE_NIGHT_START, null).getBody().path("id").asString();

    ResponseEntity<JsonNode> withoutReason =
        rest.exchange(
            "/store/attendances/" + attendanceId + "/cancellation",
            HttpMethod.POST,
            new HttpEntity<>("{}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(withoutReason.getStatusCode())
        .as("理由なしの取消は通らないこと（ADR 0013 の作法）")
        .isEqualTo(HttpStatus.BAD_REQUEST);

    assertThat(cancel(attendanceId, "重複記録のため").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(cancel(attendanceId).getStatusCode())
        .as("二度目の取消は静默冪等に委ねず撥ねること")
        .isEqualTo(HttpStatus.BAD_REQUEST);

    Attendance stored = attendanceRepository.findById(attendanceId).orElseThrow();
    assertThat(stored.isCancelled()).isTrue();
    assertThat(stored.getCancelledBy()).isNotNull();
    assertThat(stored.getCancelledReason()).as("初回の理由が二度目の要求で上書きされないこと").isEqualTo("重複記録のため");
    assertThat(stored.getActualStartAt()).as("取消は標記であって上書きではないこと").isEqualTo(LATE_NIGHT_START);
  }

  private ResponseEntity<JsonNode> record(
      String castId, String shiftId, LocalDateTime startAt, LocalDateTime endAt) {
    String body =
        "{\"cast_id\": \""
            + castId
            + "\", \"actual_start_at\": \""
            + startAt
            + "\""
            + (shiftId == null ? "" : ", \"shift_id\": \"" + shiftId + "\"")
            + (endAt == null ? "" : ", \"actual_end_at\": \"" + endAt + "\"")
            + "}";
    return rest.postForEntity(
        "/store/attendances", new HttpEntity<>(body, storeHeaders(STORE_A)), JsonNode.class);
  }

  private ResponseEntity<JsonNode> correct(
      String id, LocalDate businessDate, LocalDateTime startAt, LocalDateTime endAt) {
    String body =
        "{\"business_date\": \""
            + businessDate
            + "\", \"actual_start_at\": \""
            + startAt
            + "\""
            + (endAt == null ? "" : ", \"actual_end_at\": \"" + endAt + "\"")
            + "}";
    return rest.exchange(
        "/store/attendances/" + id,
        HttpMethod.PUT,
        new HttpEntity<>(body, storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> cancel(String id) {
    return cancel(id, "誤って記録したため");
  }

  private ResponseEntity<JsonNode> cancel(String id, String reason) {
    return rest.exchange(
        "/store/attendances/" + id + "/cancellation",
        HttpMethod.POST,
        new HttpEntity<>("{\"reason\": \"" + reason + "\"}", storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private JsonNode list(LocalDate businessDate, String castId) {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/attendances?business_date="
                + businessDate
                + (castId == null ? "" : "&cast_id=" + castId),
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    return res.getBody();
  }

  private List<String> idsOf(JsonNode rows) {
    return rows.valueStream().map(row -> row.path("id").asString()).toList();
  }

  private String createCast(String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode()).as("前提: キャスト作成が成功すること").isEqualTo(HttpStatus.CREATED);
    return created.getBody().path("id").asString();
  }

  /** 主題は実績の側なので、シフトは作成 API の検証を経由せずリポジトリ直挿しで置く。 */
  private String seedConfirmedShift(String castId, LocalDate workDate, LocalTime startTime) {
    Shift shift =
        Shift.builder()
            .castId(castId)
            .workDate(workDate)
            .startTime(startTime)
            .endTime(startTime.plusHours(8))
            .status(ShiftStatus.CONFIRMED)
            .published(true)
            .build();
    shift.setStoreId(STORE_A);
    return shiftRepository.save(shift).getId();
  }

  private void setDateChangeTime(String value) {
    systemConfigService.updateConfig(
        DATE_CHANGE_TIME_KEY, SystemConfigUpdateRequest.builder().configValue(value).build());
  }
}
