package com.kizuna.shift;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shift.domain.Attendance;
import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftStatus;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 欠勤導出を本物の PostgreSQL で確かめる統合テスト。主題は述語が DB に届いているかで、「取消済みを数えない」も
 * 「他店舗を見ない」も問い合わせの側にあり、模擬したリポジトリでは決して赤にならない。
 *
 * <p>門の時刻演算は AbsenceServiceTest が固定時計で押さえるので、こちらは門が通ることが自明な過去の営業日だけを 使う。実 DB
 * は残留しうるので、断言は自分が建てたキャストの id への包含・非包含で行う。
 */
class AbsenceDerivationIT extends CrossStoreTestSupport {

  /** 門が通ることが自明な過去の営業日。実績・シフトの直挿しで完結させるため、他 IT と衝突しない日を使う。 */
  private static final LocalDate PAST = LocalDate.of(2026, 3, 3);

  private static final String FOREIGN_STORE_DOMAIN = "absence-it-foreign.kizuna.test";

  @Autowired private CastRepository castRepository;
  @Autowired private ShiftRepository shiftRepository;
  @Autowired private AttendanceRepository attendanceRepository;
  @Autowired private StoreRepository storeRepository;

  @Test
  @DisplayName("確定シフトがあり未取消の実績が無い営業日が欠勤として導出されること")
  void derivesAbsenceFromAConfirmedShiftWithoutAttendance() {
    String absent = seedCast(STORE_A, "欠勤IT_未記録");
    String attended = seedCast(STORE_A, "欠勤IT_出勤済");
    String tentativeOnly = seedCast(STORE_A, "欠勤IT_下書きのみ");
    seedShift(STORE_A, absent, ShiftStatus.CONFIRMED);
    seedShift(STORE_A, attended, ShiftStatus.CONFIRMED);
    seedShift(STORE_A, tentativeOnly, ShiftStatus.TENTATIVE);
    seedAttendance(STORE_A, attended, false);

    List<String> castIds = absentCastIds(PAST);

    assertThat(castIds).as("確定シフトがあり実績の無いキャストが欠勤になること").contains(absent);
    assertThat(castIds).as("実績のあるキャストは欠勤にならないこと").doesNotContain(attended);
    assertThat(castIds).as("下書きシフトは欠勤の母集合に入らないこと").doesNotContain(tentativeOnly);
  }

  @Test
  @DisplayName("取消済みの実績しか無い営業日が欠勤として導出されること")
  void cancelledAttendanceDoesNotSuppressTheAbsence() {
    String castId = seedCast(STORE_A, "欠勤IT_取消のみ");
    seedShift(STORE_A, castId, ShiftStatus.CONFIRMED);
    seedAttendance(STORE_A, castId, true);

    assertThat(absentCastIds(PAST)).as("取消済みは導出から外れるので欠勤が残ること").contains(castId);

    // 正向対照: 同じ営業日に未取消の実績を足せば欠勤から外れる（取消の有無だけが差であることの証明）。
    seedAttendance(STORE_A, castId, false);
    assertThat(absentCastIds(PAST)).as("未取消の実績が 1 行でもあれば欠勤にならないこと").doesNotContain(castId);
  }

  @Test
  @DisplayName("他店舗の確定シフトが自店舗の欠勤に現れないこと")
  void foreignStoreAbsencesNeverLeak() {
    long foreignStoreId = ensureForeignStoreId();
    String foreignCastId = seedCast(foreignStoreId, "欠勤IT_他店");
    seedShift(foreignStoreId, foreignCastId, ShiftStatus.CONFIRMED);
    String ownCastId = seedCast(STORE_A, "欠勤IT_自店対照");
    seedShift(STORE_A, ownCastId, ShiftStatus.CONFIRMED);

    List<String> castIds = absentCastIds(PAST);

    assertThat(castIds).as("他店舗の実データが漏れないこと").doesNotContain(foreignCastId);
    // 正向対照: 同条件の自店舗キャストは現れる（空応答で偶然緑になっていないことの証明）。
    assertThat(castIds).contains(ownCastId);
  }

  @Test
  @DisplayName("まだ終わっていない営業日は欠勤を返さないこと")
  void derivesNothingForAnUnfinishedBusinessDate() {
    LocalDate today = LocalDate.now();
    String castId = seedCast(STORE_A, "欠勤IT_当日");
    shiftRepository.save(shiftOf(STORE_A, castId, ShiftStatus.CONFIRMED, today));

    assertThat(absentCastIds(today)).as("営業日が終わるまでは欠勤を宣言しないこと").doesNotContain(castId);
  }

  private List<String> absentCastIds(LocalDate businessDate) {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/absences?business_date=" + businessDate,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    return res.getBody().valueStream().map(row -> row.path("cast_id").asString()).toList();
  }

  private long ensureForeignStoreId() {
    return storeRepository
        .findByDomain(FOREIGN_STORE_DOMAIN)
        .orElseGet(() -> storeRepository.save(new Store("欠勤IT第二店舗", FOREIGN_STORE_DOMAIN, null)))
        .getId();
  }

  /** リポジトリ直挿（テストスレッドは {@code @StoreScoped} を経由せず storeFilter が無効なので他店舗にも書ける）。 */
  private String seedCast(long storeId, String namePrefix) {
    Cast cast = Cast.builder().name(namePrefix + "_" + System.nanoTime()).status("ACTIVE").build();
    cast.setStoreId(storeId);
    return castRepository.save(cast).getId();
  }

  private Shift shiftOf(long storeId, String castId, ShiftStatus status, LocalDate workDate) {
    Shift shift =
        Shift.builder()
            .castId(castId)
            .workDate(workDate)
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status(status)
            .published(true)
            .build();
    shift.setStoreId(storeId);
    return shift;
  }

  private void seedShift(long storeId, String castId, ShiftStatus status) {
    shiftRepository.save(shiftOf(storeId, castId, status, PAST));
  }

  private void seedAttendance(long storeId, String castId, boolean cancelled) {
    Attendance attendance =
        Attendance.record(
            castId, null, PAST, LocalDateTime.of(PAST, LocalTime.of(18, 5)), null, null, null);
    attendance.setStoreId(storeId);
    if (cancelled) {
      attendance.cancel("欠勤IT: 誤って記録したため", null, OffsetDateTime.now());
    }
    attendanceRepository.save(attendance);
  }
}
