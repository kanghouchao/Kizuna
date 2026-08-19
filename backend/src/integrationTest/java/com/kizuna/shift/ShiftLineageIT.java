package com.kizuna.shift;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftStatus;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
 * 系列照会（希望 → 承認 → 変更申請 → 当日実績）を本物の PostgreSQL で端から端まで辿る統合テスト。
 *
 * <p>申請は模擬せず本人ポータルから実際に提出して店舗が承認する。背骨は申請側の shift_id 回写なので、直挿しの 行を並べただけでは「承認が背骨を結んだ」ことは証明できない。
 *
 * <p>実行主体の期待値は種子の身分から引く — 名前まで解決することがこの読み口の要点で、id だけなら店舗側の呼び手には 誰のことか分からない。
 */
class ShiftLineageIT extends CrossStoreTestSupport {

  private static final String CAST_EMAIL = "lineage-it-cast@kizuna.test";
  private static final String PASSWORD = "pass";

  /** {@code storeHeaders} が名乗る v0.1.0 seed/05-demo.yaml の店舗スタッフ。承認・記録の実行者の期待値をここから引く。 */
  private static final String SEED_STORE_STAFF_EMAIL = "yamada.jiro@kizuna.test";

  private static final String FOREIGN_STORE_DOMAIN = "lineage-it-foreign.kizuna.test";

  @Autowired private CastRepository castRepository;
  @Autowired private ShiftRepository shiftRepository;
  @Autowired private StoreRepository storeRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private String myCastId;
  private String castToken;

  @BeforeEach
  void prepareFixture() {
    Long castUserId = ensureCastUser().getId();
    myCastId = ensureCast(castUserId);
    castToken = platformToken(CAST_EMAIL);
  }

  @Test
  @DisplayName("希望の承認で生まれたシフトから、申請と実績が実行主体・日時付きで辿れること")
  void lineageWalksFromTheShiftBackToTheRequestAndForwardToTheAttendance() {
    PlatformUser staff = platformUserRepository.findByEmail(SEED_STORE_STAFF_EMAIL).orElseThrow();
    LocalDate workDate = LocalDate.now().plusDays(3);

    String newRequestId = submitNewRequest(workDate);
    String shiftId = approve(newRequestId).path("shift_id").asString();
    assertThat(shiftId).as("前提: 承認が確定シフトを生むこと").isNotBlank();

    String changeRequestId = submitChangeRequest(shiftId, workDate);
    approve(changeRequestId);

    String attendanceId = recordAttendance(shiftId, workDate);

    JsonNode detail = detail(shiftId);

    assertThat(detail.path("id").asString()).isEqualTo(shiftId);
    assertThat(detail.path("status").asString()).isEqualTo("CONFIRMED");
    assertThat(detail.path("created_by").path("id").asLong())
        .as("承認で生まれた行の作成者は承認者であること")
        .isEqualTo(staff.getId());
    assertThat(detail.path("created_by").path("name").asString())
        .as("実行主体は名前まで解決されること")
        .isEqualTo(staff.getDisplayName());

    JsonNode origin = detail.path("origin");
    assertThat(origin.path("id").asString()).as("出生の希望が背骨で辿れること").isEqualTo(newRequestId);
    assertThat(origin.path("type").asString()).isEqualTo("NEW");
    assertThat(origin.path("status").asString()).isEqualTo("APPROVED");
    assertThat(origin.path("cast_id").asString()).as("申請の実行主体はキャスト本人").isEqualTo(myCastId);
    assertThat(origin.path("processed_by").path("name").asString())
        .isEqualTo(staff.getDisplayName());
    assertThat(origin.path("processed_at").asString()).as("承認の時刻が残ること").isNotBlank();
    assertThat(origin.path("created_at").asString()).as("提出の時刻が残ること").isNotBlank();

    JsonNode changeRequests = changeRequests(shiftId, null);
    assertThat(
            changeRequests.path("content").valueStream().map(r -> r.path("id").asString()).toList())
        .as("変更申請は詳細ではなくカーソルの読み口から辿れること")
        .containsExactly(changeRequestId);
    assertThat(changeRequests.path("content").get(0).path("type").asString()).isEqualTo("CHANGE");
    assertThat(changeRequests.path("content").get(0).path("processed_by").path("name").asString())
        .isEqualTo(staff.getDisplayName());
    assertThat(changeRequests.has("next_cursor")).as("続きが無ければカーソルは出ないこと").isFalse();

    JsonNode attendance = detail.path("attendance");
    assertThat(attendance.path("id").asString()).isEqualTo(attendanceId);
    assertThat(attendance.path("created_by").path("name").asString())
        .isEqualTo(staff.getDisplayName());
    assertThat(attendance.path("business_date").asString())
        .as("実績の営業日はシフトの勤務日を継承すること")
        .isEqualTo(workDate.toString());

    // 取消済みは導出・照会から外れる（ADR 0014）。系列の末端も同じ規則に従う。
    cancelAttendance(attendanceId);
    assertThat(detail(shiftId).has("attendance")).as("取消済みの実績は系列に残らないこと").isFalse();
  }

  @Test
  @DisplayName("店舗が直接作成したシフトは申請も実績も持たないこと")
  void directlyCreatedShiftHasAnEmptyLineage() {
    String shiftId = createShiftDirectly();
    JsonNode detail = detail(shiftId);

    assertThat(detail.has("origin")).isFalse();
    assertThat(detail.has("attendance")).isFalse();
    assertThat(changeRequests(shiftId, null).path("content")).isEmpty();
  }

  @Test
  @DisplayName("変更申請履歴がカーソルで重複・欠落なく辿れること")
  void changeRequestHistoryWalksThroughTheCursor() {
    LocalDate workDate = LocalDate.now().plusDays(4);
    String shiftId = approve(submitNewRequest(workDate)).path("shift_id").asString();

    // 同じシフトへ変更申請を重ねる。件数の上限も一意性の守衛も無いので、実際に積み上がることを示す。
    List<String> submitted =
        List.of(
            submitChangeRequest(shiftId, workDate, "19:00:00", "23:30:00"),
            submitChangeRequest(shiftId, workDate, "19:15:00", "23:45:00"),
            submitChangeRequest(shiftId, workDate, "19:30:00", "23:50:00"));

    List<String> walked = new ArrayList<>();
    String cursor = null;
    do {
      JsonNode page = changeRequests(shiftId, cursor);
      page.path("content").valueStream().forEach(r -> walked.add(r.path("id").asString()));
      cursor = page.has("next_cursor") ? page.path("next_cursor").asString() : null;
    } while (cursor != null);

    assertThat(walked).as("1 件ずつ辿っても重複も欠落もないこと").containsExactlyInAnyOrderElementsOf(submitted);
  }

  @Test
  @DisplayName("他店舗のシフトは詳細で見えないこと")
  void foreignShiftIsNotVisible() {
    long foreignStoreId =
        storeRepository
            .findByDomain(FOREIGN_STORE_DOMAIN)
            .orElseGet(
                () -> storeRepository.save(new Store("系列照会IT第二店舗", FOREIGN_STORE_DOMAIN, null)))
            .getId();
    Cast foreignCast = Cast.builder().name("系列照会IT他店キャスト").status("ACTIVE").build();
    foreignCast.setStoreId(foreignStoreId);
    Shift foreignShift =
        Shift.builder()
            .castId(castRepository.save(foreignCast).getId())
            .workDate(LocalDate.now().plusDays(3))
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(23, 0))
            .status(ShiftStatus.CONFIRMED)
            .published(true)
            .build();
    foreignShift.setStoreId(foreignStoreId);
    String foreignShiftId = shiftRepository.save(foreignShift).getId();

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/shifts/" + foreignShiftId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(res.getStatusCode()).as("他店舗のシフトは存在しないものとして見えること").isEqualTo(HttpStatus.NOT_FOUND);
    // 正向対照: 自店舗のシフトは同じ口で 200 になる（404 が経路の不備でないことの証明）。
    assertThat(
            rest.exchange(
                    "/store/shifts/" + createShiftDirectly(),
                    HttpMethod.GET,
                    new HttpEntity<>(storeHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  private JsonNode changeRequests(String shiftId, String cursor) {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/shifts/"
                + shiftId
                + "/change-requests?size=1"
                + (cursor == null ? "" : "&cursor=" + cursor),
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    return res.getBody();
  }

  private JsonNode detail(String shiftId) {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/shifts/" + shiftId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    return res.getBody();
  }

  private String createShiftDirectly() {
    String castId = myCastId;
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/shifts",
            new HttpEntity<>(
                "{\"cast_id\": \""
                    + castId
                    + "\", \"work_date\": \""
                    + LocalDate.now().plusDays(5)
                    + "\", \"start_time\": \"18:00:00\", \"end_time\": \"23:00:00\"}",
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode()).as("前提: 直接作成が成功すること").isEqualTo(HttpStatus.CREATED);
    return created.getBody().path("id").asString();
  }

  private String submitNewRequest(LocalDate workDate) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/me/shift-requests",
            new HttpEntity<>(
                "{\"store_id\": "
                    + STORE_A
                    + ", \"work_date\": \""
                    + workDate
                    + "\", \"start_time\": \"18:00:00\", \"end_time\": \"23:00:00\","
                    + " \"note\": \"系列照会IT\"}",
                bearer(castToken)),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 出勤希望の提出が成功すること").isEqualTo(HttpStatus.CREATED);
    return res.getBody().path("id").asString();
  }

  private String submitChangeRequest(String shiftId, LocalDate workDate) {
    return submitChangeRequest(shiftId, workDate, "19:00:00", "23:30:00");
  }

  private String submitChangeRequest(
      String shiftId, LocalDate workDate, String startTime, String endTime) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/me/shift-requests/changes",
            new HttpEntity<>(
                "{\"shift_id\": \""
                    + shiftId
                    + "\", \"work_date\": \""
                    + workDate
                    + "\", \"start_time\": \""
                    + startTime
                    + "\", \"end_time\": \""
                    + endTime
                    + "\"}",
                bearer(castToken)),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 変更申請の提出が成功すること").isEqualTo(HttpStatus.CREATED);
    return res.getBody().path("id").asString();
  }

  private JsonNode approve(String requestId) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/store/shift-requests/" + requestId + "/approval",
            new HttpEntity<>("{}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 承認が成功すること").isEqualTo(HttpStatus.OK);
    return res.getBody();
  }

  private String recordAttendance(String shiftId, LocalDate workDate) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/store/attendances",
            new HttpEntity<>(
                "{\"cast_id\": \""
                    + myCastId
                    + "\", \"shift_id\": \""
                    + shiftId
                    + "\", \"actual_start_at\": \""
                    + workDate.atTime(LocalTime.of(19, 5))
                    + "\", \"waiting_place\": \"1番待機室\"}",
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 実績の記録が成功すること").isEqualTo(HttpStatus.CREATED);
    return res.getBody().path("id").asString();
  }

  private void cancelAttendance(String attendanceId) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/store/attendances/" + attendanceId + "/cancellation",
            new HttpEntity<>("{\"reason\": \"系列照会IT: 誤記録\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 実績の取消が成功すること").isEqualTo(HttpStatus.NO_CONTENT);
  }

  /** 本人の cast 行を find-or-create する（@BeforeEach はテストメソッドごとに走るため、単純作成だと行が複製される）。 */
  private String ensureCast(Long platformUserId) {
    return castRepository.findIdsByPlatformUserIdAndStoreId(platformUserId, STORE_A).stream()
        .findFirst()
        .orElseGet(
            () -> {
              Cast cast =
                  Cast.builder()
                      .name("系列照会IT本人")
                      .status("ACTIVE")
                      .platformUserId(platformUserId)
                      .build();
              cast.setStoreId(STORE_A);
              return castRepository.save(cast).getId();
            });
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
                        .displayName("系列照会IT キャスト")
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
    assertThat(res.getStatusCode()).as("前提: 平台ログインが成功すること").isEqualTo(HttpStatus.OK);
    return res.getBody().path("token").asString();
  }

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(token);
    return headers;
  }
}
