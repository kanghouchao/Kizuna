package com.kizuna.shift;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftRequestRepository;
import com.kizuna.shift.domain.ShiftRequestType;
import com.kizuna.shift.domain.ShiftStatus;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

/**
 * 予定（シフト）と実績の交差に置いた守衛を本物の PostgreSQL で確かめる統合テスト（ADR 0014）。
 *
 * <p>三面（変更申請の提出・承認・承認可否導出）の同条件性は、固定具を面ごとに分けては示せない — 面ごとにシフトの取り出し方が違うので、
 * 別々の固定具では「たまたま三面とも拒否になった」と区別できない。ひとつのシフトと 1 本の実績に三面を当て、 実績の取消という**ただ一つの変更**で三面が揃って通ることを見る。
 */
class ShiftAttendanceGuardIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "pass";
  private static final String CAST_EMAIL = "shift-attendance-guard-it-cast@kizuna.test";

  /** 現在の営業日より確実に後。承認・提出に掛かる営業日の関門を主題から外す。 */
  private static final LocalDate WORK_DATE = LocalDate.of(2999, 6, 1);

  private static final LocalDate OTHER_WORK_DATE = LocalDate.of(2999, 6, 3);

  @Autowired private CastRepository castRepository;
  @Autowired private ShiftRepository shiftRepository;
  @Autowired private ShiftRequestRepository shiftRequestRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private Long castUserId;
  private String castToken;

  @BeforeEach
  void prepareCastIdentity() {
    castUserId = ensureCastUser().getId();
    castToken = platformToken(CAST_EMAIL);
  }

  @Test
  @DisplayName("未取消実績付きシフトの勤務日・キャストが変更できず、時刻と status は通り、実績の取消後は変更できること")
  void activeAttendanceFreezesAttributionButNotTimesOrStatus() {
    String castId = newCast();
    String shiftId = seedConfirmedShift(castId);
    String attendanceId = recordAttendance(castId, shiftId);

    assertThat(updateShift(shiftId, "{\"work_date\": \"" + OTHER_WORK_DATE + "\"}"))
        .satisfies(res -> assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
        .satisfies(
            res -> assertThat(res.getBody().path("error").asString()).contains("勤務日とキャストは変更できません"));

    String otherCastId = newCast();
    assertThat(updateShift(shiftId, "{\"cast_id\": \"" + otherCastId + "\"}").getStatusCode())
        .as("キャストの付け替えも拒まれること")
        .isEqualTo(HttpStatus.BAD_REQUEST);

    // 実績の有無と無関係に通す面。ここが赤なら禁改が時刻・status まで巻き込んでいる。
    assertThat(updateShift(shiftId, "{\"start_time\": \"19:00:00\"}").getStatusCode())
        .as("時刻の変更は通ること")
        .isEqualTo(HttpStatus.OK);
    assertThat(updateShift(shiftId, "{\"status\": \"TENTATIVE\"}").getStatusCode())
        .as("status の変更は通ること")
        .isEqualTo(HttpStatus.OK);

    cancelAttendance(attendanceId);

    ResponseEntity<JsonNode> afterCancel =
        updateShift(shiftId, "{\"work_date\": \"" + OTHER_WORK_DATE + "\"}");
    assertThat(afterCancel.getStatusCode())
        .as("取消 → 変更 → 再記録の逃げ道が開いていること")
        .isEqualTo(HttpStatus.OK);
    assertThat(afterCancel.getBody().path("work_date").asString())
        .isEqualTo(OTHER_WORK_DATE.toString());
  }

  @Test
  @DisplayName("実績が参照するシフトは取消済みでも削除できず、実績なしのシフトは従来通り削除できること")
  void anyAttendanceReferenceBlocksShiftDeletion() {
    String castId = newCast();
    String shiftId = seedConfirmedShift(castId);
    String attendanceId = recordAttendance(castId, shiftId);

    ResponseEntity<JsonNode> refused = deleteShift(shiftId);
    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(refused.getBody().path("error").asString()).contains("実績が記録されているシフトは削除できません");

    cancelAttendance(attendanceId);

    // 文言まで見る。守衛が未取消しか数えなければ書き込みは外部キーに当たるが、その制約は DbConstraint に
    // 登録が無いので 500 になる。status だけでは写像の抜けと守衛の抜けを取り違えるので、文言で決める。
    ResponseEntity<JsonNode> refusedAfterCancel = deleteShift(shiftId);
    assertThat(refusedAfterCancel.getStatusCode())
        .as("取消済みの実績も参照であることに変わりはないこと")
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(refusedAfterCancel.getBody().path("error").asString())
        .isEqualTo("実績が記録されているシフトは削除できません。実績を取り消したうえで下書きに戻してください");
    assertThat(shiftRepository.findById(shiftId)).as("拒否されたシフトは残存すること").isPresent();

    // 正の対照: 実績を持たないシフトの削除は従来通り成立する。
    String untouchedShiftId = seedConfirmedShift(newCast());
    assertThat(deleteShift(untouchedShiftId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  @DisplayName("未取消実績付きシフトへの変更申請が提出・承認・承認可否導出の三面で拒まれ、実績の取消だけで三面とも通ること")
  void changeRequestIsRefusedOnAllThreeSurfacesUntilTheAttendanceIsCancelled() {
    String castId = newCast();
    String shiftId = seedConfirmedShift(castId);
    String attendanceId = recordAttendance(castId, shiftId);
    String pendingRequestId = seedPendingChangeRequest(castId, shiftId);

    ResponseEntity<JsonNode> submitted = submitChange(shiftId);
    assertThat(submitted.getStatusCode()).as("提出の面").isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(submitted.getBody().path("error").asString()).contains("実績が記録されているシフト");

    ResponseEntity<JsonNode> approved = approve(pendingRequestId);
    assertThat(approved.getStatusCode()).as("承認の面").isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(approved.getBody().path("error").asString()).contains("実績が記録されているシフト");

    assertThat(approvableOf(pendingRequestId)).as("承認可否導出の面").isFalse();

    // 変えるのはこの 1 点だけ。他の条件は一切動かさない。
    cancelAttendance(attendanceId);

    assertThat(approvableOf(pendingRequestId)).as("取消だけで承認可否導出が翻ること").isTrue();
    assertThat(submitChange(shiftId).getStatusCode())
        .as("取消だけで提出が通ること")
        .isEqualTo(HttpStatus.CREATED);
    assertThat(approve(pendingRequestId).getStatusCode())
        .as("取消だけで承認が通ること")
        .isEqualTo(HttpStatus.OK);
  }

  private String newCast() {
    Cast cast =
        Cast.builder()
            .name("予実守衛IT-" + UUID.randomUUID())
            .status("ACTIVE")
            .platformUserId(castUserId)
            .build();
    cast.setStoreId(STORE_A);
    return castRepository.save(cast).getId();
  }

  /** 主題は交差の守衛なので、シフトは作成 API の検証を経由せずリポジトリ直挿しで置く。 */
  private String seedConfirmedShift(String castId) {
    Shift shift =
        Shift.builder()
            .castId(castId)
            .workDate(WORK_DATE)
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status(ShiftStatus.CONFIRMED)
            .published(true)
            .build();
    shift.setStoreId(STORE_A);
    return shiftRepository.save(shift).getId();
  }

  /** 承認の面を叩くための PENDING な変更申請。original_* は対象シフトの現況に一致させる（陳腐化で落とさない）。 */
  private String seedPendingChangeRequest(String castId, String shiftId) {
    ShiftRequest request =
        ShiftRequest.builder()
            .castId(castId)
            .type(ShiftRequestType.CHANGE)
            .shiftId(shiftId)
            .originalWorkDate(WORK_DATE)
            .originalStartTime(LocalTime.of(18, 0))
            .originalEndTime(LocalTime.of(23, 0))
            .workDate(WORK_DATE)
            .startTime(LocalTime.of(19, 0))
            .endTime(LocalTime.of(22, 0))
            .build();
    request.setStoreId(STORE_A);
    return shiftRequestRepository.save(request).getId();
  }

  private String recordAttendance(String castId, String shiftId) {
    String body =
        "{\"cast_id\": \""
            + castId
            + "\", \"shift_id\": \""
            + shiftId
            + "\", \"actual_start_at\": \""
            + LocalDateTime.of(WORK_DATE, LocalTime.of(18, 5))
            + "\"}";
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/attendances", new HttpEntity<>(body, storeHeaders(STORE_A)), JsonNode.class);
    assertThat(created.getStatusCode()).as("前提: 実績の記録が成功すること").isEqualTo(HttpStatus.CREATED);
    return created.getBody().path("id").asString();
  }

  private void cancelAttendance(String attendanceId) {
    ResponseEntity<JsonNode> cancelled =
        rest.exchange(
            "/store/attendances/" + attendanceId + "/cancellation",
            HttpMethod.POST,
            new HttpEntity<>("{\"reason\": \"予実守衛ITの取消\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(cancelled.getStatusCode()).as("前提: 実績の取消が成功すること").isEqualTo(HttpStatus.NO_CONTENT);
  }

  private ResponseEntity<JsonNode> updateShift(String shiftId, String body) {
    return rest.exchange(
        "/store/shifts/" + shiftId,
        HttpMethod.PUT,
        new HttpEntity<>(body, storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> deleteShift(String shiftId) {
    return rest.exchange(
        "/store/shifts/" + shiftId,
        HttpMethod.DELETE,
        new HttpEntity<>(storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> submitChange(String shiftId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(castToken);
    String body =
        "{\"shift_id\": \""
            + shiftId
            + "\", \"work_date\": \""
            + WORK_DATE
            + "\", \"start_time\": \"20:00:00\", \"end_time\": \"23:30:00\"}";
    return rest.postForEntity(
        "/platform/me/shift-requests/changes", new HttpEntity<>(body, headers), JsonNode.class);
  }

  private ResponseEntity<JsonNode> approve(String requestId) {
    return rest.postForEntity(
        "/store/shift-requests/" + requestId + "/approval",
        new HttpEntity<>("{}", storeHeaders(STORE_A)),
        JsonNode.class);
  }

  /** inbox が当該申請に付ける承認可否。一覧は同店舗の他の申請も返すので、id で当の行を選ぶ。 */
  private boolean approvableOf(String requestId) {
    ResponseEntity<JsonNode> listed =
        rest.exchange(
            "/store/shift-requests?status=PENDING",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
    return listed
        .getBody()
        .valueStream()
        .filter(row -> requestId.equals(row.path("id").asString()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("前提: 申請が inbox に現れること " + requestId))
        .path("approvable")
        .asBoolean();
  }

  private PlatformUser ensureCastUser() {
    return platformUserRepository
        .findByEmail(CAST_EMAIL)
        .orElseGet(
            () ->
                platformUserRepository.save(
                    PlatformUser.builder()
                        .email(CAST_EMAIL)
                        .password(passwordEncoder.encode(PASSWORD))
                        .displayName("予実守衛IT キャスト")
                        .enabled(true)
                        .userType(UserType.CAST)
                        .roleIds(Set.of())
                        .storeScopeType(StoreScopeType.ALL_STORES)
                        .storeIds(Set.of())
                        .build()));
  }

  private String platformToken(String email) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}", headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: キャストの平台ログインが成功すること").isEqualTo(HttpStatus.OK);
    return res.getBody().path("token").asString();
  }
}
