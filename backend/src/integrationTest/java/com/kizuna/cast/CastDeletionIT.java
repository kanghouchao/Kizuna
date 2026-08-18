package com.kizuna.cast;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * キャスト削除の可否を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>受注から参照されているキャストは外部キーが削除を止める。止めること自体は正しい — 過去の受注が誰の担当だったかは売上の根拠であり、消えてよい参照ではない。
 * 固定するのは応答のほうで、これは日常操作で当たる競合なので 500 ではなく次の一手の読める 409 になる。
 *
 * <p>当日実績からの参照も同じく削除を止める（ADR 0014）。こちらは飛び込みとシフト紐づきの二形を分けて見る — キャストの削除はシフトへ連鎖するので、
 * シフト紐づきでは実績のシフト側の外部キーが先に鳴りうる。どちらの形でも同じ断りが返ることが要点である。
 */
class CastDeletionIT extends CrossStoreTestSupport {

  /** demo シード（seed/05-demo.yaml）の山田次郎（STORE_STAFF・授権店舗 = 店舗1）。受注の受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  /** 実績の帰属営業日。同クラスの実績どうしが（キャスト, 店舗, 営業日）の一意に当たらないよう、キャストは毎回作り直す。 */
  private static final LocalDate ATTENDANCE_DATE = LocalDate.of(2999, 6, 1);

  @Autowired private ShiftRepository shiftRepository;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("受注から参照されているキャストの削除が、案内の読める 409 になること")
  void rejectsDeletingCastReferencedByOrder() {
    String castId = createCast("受注あり");
    createOrderFor(castId);

    ResponseEntity<JsonNode> response = delete(castId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().path("error").asString())
        .isEqualTo("受注が紐づいているキャストは削除できません。在籍停止に変更してください");
    assertThat(get(castId).getStatusCode()).as("削除は成立していないこと").isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("正の対照: 受注から参照されていないキャストは削除できること")
  void deletesCastWithoutOrders() {
    String castId = createCast("受注なし");

    assertThat(delete(castId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(get(castId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("飛び込み実績に参照されているキャストの削除が、実績を名指す 409 になること")
  void rejectsDeletingCastReferencedByWalkInAttendance() {
    String castId = createCast("飛び込み実績あり");
    recordAttendance(castId, null);

    ResponseEntity<JsonNode> response = delete(castId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().path("error").asString())
        .isEqualTo("実績が記録されているキャストは削除できません。在籍停止に変更してください");
    assertThat(get(castId).getStatusCode()).as("削除は成立していないこと").isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("実績のキャストと違うキャストへ付け替えられたシフトでも、その引受先キャストの削除が 409 になること")
  void rejectsDeletingCastWhoseShiftIsReferencedByAnotherCastsAttendance() {
    // 実績の cast_id とシフトの cast_id は永久に一致するとは限らない。実績を取り消せばシフトの
    // 付け替えが解禁されるので（それ自体は #732 の求める挙動）、実績が指すシフトを引き受けた別の
    // キャストが生まれる。そのキャストの削除はシフトへ連鎖し、実績のシフト側外部キーに当たる。
    String recordedCastId = createCast("実績の主");
    String shiftId = seedConfirmedShift(recordedCastId);
    String attendanceId = recordAttendance(recordedCastId, shiftId);
    cancelAttendance(attendanceId);

    String inheritingCastId = createCast("付け替え先");
    reassignShift(shiftId, inheritingCastId);

    ResponseEntity<JsonNode> response = delete(inheritingCastId);

    assertThat(response.getStatusCode())
        .as("シフトへの連鎖が実績を道連れにしようとする経路も止まること")
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().path("error").asString())
        .as("どちらの外部キーが鳴りうる経路でも断りの文言が変わらないこと")
        .isEqualTo("実績が記録されているキャストは削除できません。在籍停止に変更してください");
    assertThat(get(inheritingCastId).getStatusCode()).as("削除は成立していないこと").isEqualTo(HttpStatus.OK);
  }

  private void reassignShift(String shiftId, String castId) {
    ResponseEntity<JsonNode> updated =
        rest.exchange(
            "/store/shifts/" + shiftId,
            HttpMethod.PUT,
            new HttpEntity<>("{\"cast_id\": \"" + castId + "\"}", managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(updated.getStatusCode()).as("前提: 取消済みの実績はシフトの付け替えを妨げないこと").isEqualTo(HttpStatus.OK);
  }

  private void cancelAttendance(String attendanceId) {
    ResponseEntity<JsonNode> cancelled =
        rest.exchange(
            "/store/attendances/" + attendanceId + "/cancellation",
            HttpMethod.POST,
            new HttpEntity<>("{\"reason\": \"キャスト削除ITの取消\"}", managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(cancelled.getStatusCode()).as("前提: 実績の取消が成功すること").isEqualTo(HttpStatus.NO_CONTENT);
  }

  private String seedConfirmedShift(String castId) {
    Shift shift =
        Shift.builder()
            .castId(castId)
            .workDate(ATTENDANCE_DATE)
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status(ShiftStatus.CONFIRMED)
            .published(true)
            .build();
    shift.setStoreId(STORE_A);
    return shiftRepository.save(shift).getId();
  }

  private String recordAttendance(String castId, String shiftId) {
    String body =
        "{\"cast_id\": \""
            + castId
            + "\", \"actual_start_at\": \""
            + LocalDateTime.of(ATTENDANCE_DATE, LocalTime.of(18, 5))
            + "\""
            + (shiftId == null ? "" : ", \"shift_id\": \"" + shiftId + "\"")
            + "}";
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/attendances", new HttpEntity<>(body, managerHeaders(STORE_A)), JsonNode.class);
    assertThat(created.getStatusCode()).as("前提: 実績の記録が成功すること").isEqualTo(HttpStatus.CREATED);
    return created.getBody().path("id").asString();
  }

  private String createCast(String label) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>(
                "{\"name\": \"削除検証-" + label + "-" + nonce + "\"}", managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: キャスト作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private void createOrderFor(String castId) {
    String body =
        "{\"receptionist_id\": "
            + SEED_RECEPTIONIST_ID
            + ", \"business_date\": \""
            + LocalDate.now()
            + "\", \"cast_id\": \""
            + castId
            + "\", \"pax\": 2}";
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders", new HttpEntity<>(body, managerHeaders(STORE_A)), JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 受注作成が成功すること").isTrue();
  }

  private ResponseEntity<JsonNode> delete(String castId) {
    return rest.exchange(
        "/store/casts/" + castId,
        HttpMethod.DELETE,
        new HttpEntity<>(managerHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> get(String castId) {
    return rest.exchange(
        "/store/casts/" + castId,
        HttpMethod.GET,
        new HttpEntity<>(managerHeaders(STORE_A)),
        JsonNode.class);
  }
}
