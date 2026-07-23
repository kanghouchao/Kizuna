package com.kizuna.shift;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftRequestRepository;
import com.kizuna.shift.domain.ShiftRequestStatus;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
 * 出勤希望（提出+店舗側 inbox 承認）の跨店隔離・状態遷移・承認によるシフト確定反映を本物の PostgreSQL で強断言する統合テスト。
 *
 * <p>断言は「帰属不一致」型の弱断言ではなく、応答生ボディに授権外の実データ（別キャストの希望・非所属店舗の希望）が一切現れないことを強断言する。 先例は {@link
 * PlatformScheduleScopeIT}（cast_id 単層自限 + 実データ断言）と {@link ShiftCrossStoreIT}（単店隔離）。
 */
class ShiftRequestScopeIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "pass";
  private static final String CAST_EMAIL = "shift-request-it-cast@kizuna.test";

  /** v0.1.0 seed/05-demo.yaml の店舗スタッフ（ROLE_CAST を持たない）。役割線 403 の検証に使う。 */
  private static final String NON_CAST_STAFF_EMAIL = "yamada.jiro@kizuna.test";

  private static final String STORE_FOREIGN_DOMAIN = "shift-request-it-foreign.kizuna.test";

  @Autowired private CastRepository castRepository;
  @Autowired private ShiftRequestRepository shiftRequestRepository;
  @Autowired private ShiftRepository shiftRepository;
  @Autowired private StoreRepository storeRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private long storeForeignId;
  private Long castUserId;
  private String myCastId;
  private String castToken;

  @BeforeEach
  void prepareFixture() {
    storeForeignId = ensureStore(STORE_FOREIGN_DOMAIN, "出勤希望IT非所属店舗");
    castUserId =
        ensurePlatformUser(CAST_EMAIL, UserType.CAST, StoreScopeType.ALL_STORES, Set.of()).getId();
    myCastId = ensureCast(STORE_A, "出勤希望IT本人", castUserId);
    castToken = platformToken(CAST_EMAIL, PASSWORD);
  }

  /**
   * 本人の cast 行を find-or-create する。@BeforeEach は各テストメソッドの前に走るため、単純作成だと 同一 (platformUserId, storeId)
   * の行がテストメソッド数だけ複製される。既存行があれば最古の行を使う。
   */
  private String ensureCast(long storeId, String name, Long platformUserId) {
    return castRepository.findIdsByPlatformUserIdAndStoreId(platformUserId, storeId).stream()
        .findFirst()
        .orElseGet(() -> createCast(storeId, name, platformUserId));
  }

  private long ensureStore(String domain, String name) {
    return storeRepository
        .findByDomain(domain)
        .orElseGet(() -> storeRepository.save(new Store(name, domain, null)))
        .getId();
  }

  private PlatformUser ensurePlatformUser(
      String email, UserType userType, StoreScopeType scopeType, Set<Long> storeIds) {
    return platformUserRepository
        .findByEmail(email)
        .orElseGet(
            () ->
                platformUserRepository.save(
                    PlatformUser.builder()
                        .email(email)
                        .password(passwordEncoder.encode(PASSWORD))
                        .displayName("出勤希望IT " + userType.name())
                        .enabled(true)
                        .userType(userType)
                        .bundleIds(Set.of())
                        .storeScopeType(scopeType)
                        .storeIds(storeIds)
                        .build()));
  }

  /** リポジトリ直挿（テストスレッドは @StoreScoped を経由せず storeFilter が無効なので他店舗にも書ける）。 */
  private String createCast(long storeId, String name, Long platformUserId) {
    Cast cast = Cast.builder().name(name).status("ACTIVE").platformUserId(platformUserId).build();
    cast.setStoreId(storeId);
    return castRepository.save(cast).getId();
  }

  private String saveShiftRequest(
      String castId,
      long storeId,
      LocalDate workDate,
      LocalTime start,
      LocalTime end,
      String note,
      ShiftRequestStatus status) {
    ShiftRequest request =
        ShiftRequest.builder()
            .castId(castId)
            .workDate(workDate)
            .startTime(start)
            .endTime(end)
            .note(note)
            .status(status)
            .build();
    request.setStoreId(storeId);
    return shiftRequestRepository.save(request).getId();
  }

  private String platformToken(String email, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, password),
                headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 平台ログインが成功すること").isEqualTo(HttpStatus.OK);
    String t = res.getBody().path("token").asString();
    assertThat(t).isNotBlank();
    return t;
  }

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(token);
    return headers;
  }

  private String submitBody(long storeId, String workDate, String start, String end, String note) {
    return "{\"store_id\": "
        + storeId
        + ", \"work_date\": \""
        + workDate
        + "\", \"start_time\": \""
        + start
        + "\", \"end_time\": \""
        + end
        + "\", \"note\": \""
        + note
        + "\"}";
  }

  private ResponseEntity<JsonNode> submit(String token, String body) {
    return rest.postForEntity(
        "/platform/me/shift-requests", new HttpEntity<>(body, bearer(token)), JsonNode.class);
  }

  private ResponseEntity<JsonNode> getHistory(String token) {
    return rest.exchange(
        "/platform/me/shift-requests",
        HttpMethod.GET,
        new HttpEntity<>(bearer(token)),
        JsonNode.class);
  }

  private ResponseEntity<String> getHistoryRaw(String token) {
    return rest.exchange(
        "/platform/me/shift-requests",
        HttpMethod.GET,
        new HttpEntity<>(bearer(token)),
        String.class);
  }

  private ResponseEntity<String> getInboxRaw(long storeId, String status) {
    String url =
        status == null ? "/store/shift-requests" : "/store/shift-requests?status=" + status;
    return rest.exchange(
        url, HttpMethod.GET, new HttpEntity<>(storeHeaders(storeId)), String.class);
  }

  private ResponseEntity<JsonNode> approve(long storeId, String id) {
    return rest.exchange(
        "/store/shift-requests/" + id + "/approval",
        HttpMethod.POST,
        new HttpEntity<>(storeHeaders(storeId)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> decline(long storeId, String id) {
    return rest.exchange(
        "/store/shift-requests/" + id + "/decline",
        HttpMethod.POST,
        new HttpEntity<>(storeHeaders(storeId)),
        JsonNode.class);
  }

  private boolean historyContainsId(JsonNode body, String id) {
    for (JsonNode node : body) {
      if (id.equals(node.path("id").asString())) {
        return true;
      }
    }
    return false;
  }

  private String tomorrow() {
    return LocalDate.now(ZoneId.of("Asia/Tokyo")).plusDays(1).toString();
  }

  private String today() {
    return LocalDate.now(ZoneId.of("Asia/Tokyo")).toString();
  }

  @Test
  @DisplayName("所属店への提出が成功し、履歴に受付済み(PENDING)として現れること(正向対照)")
  void submitToAffiliatedStore_succeedsAndAppearsInHistoryAsPending() {
    ResponseEntity<JsonNode> created =
        submit(castToken, submitBody(STORE_A, tomorrow(), "18:00:00", "23:00:00", "よろしくお願いします"));

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = created.getBody().path("id").asString();
    assertThat(id).isNotBlank();
    assertThat(created.getBody().path("status").asString()).isEqualTo("PENDING");

    ResponseEntity<JsonNode> history = getHistory(castToken);
    assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(historyContainsId(history.getBody(), id)).isTrue();
  }

  @Test
  @DisplayName("非所属店舗への提出は拒否され、テーブルに行が増えないこと")
  void submitToNonAffiliatedStore_isRejectedAndCreatesNoRow() {
    long before = shiftRequestRepository.count();

    ResponseEntity<JsonNode> res =
        submit(
            castToken, submitBody(storeForeignId, tomorrow(), "18:00:00", "23:00:00", "非所属店への提出"));

    assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    assertThat(shiftRequestRepository.count()).as("拒否された提出は行を作らないこと").isEqualTo(before);
  }

  @Test
  @DisplayName("履歴に別キャスト・非所属店舗の実データが一切現れないこと(強断言)")
  void history_neverLeaksOtherCastOrForeignStoreRealData() {
    String canaryNote = "履歴漏洩カナリア_" + UUID.randomUUID();
    String foreignCastInStoreA = createCast(STORE_A, "出勤希望IT別キャスト", null);
    saveShiftRequest(
        foreignCastInStoreA,
        STORE_A,
        LocalDate.of(2999, 4, 3),
        LocalTime.of(23, 57),
        LocalTime.of(23, 59),
        canaryNote,
        ShiftRequestStatus.PENDING);

    String foreignStoreName = "履歴漏洩カナリア店舗_" + UUID.randomUUID();
    long canaryForeignStoreId =
        ensureStore("shift-request-it-canary.kizuna.test", foreignStoreName);
    String foreignCastInForeignStore = createCast(canaryForeignStoreId, "出勤希望IT非所属店キャスト", null);
    saveShiftRequest(
        foreignCastInForeignStore,
        canaryForeignStoreId,
        LocalDate.of(2999, 4, 3),
        LocalTime.of(14, 0),
        LocalTime.of(16, 0),
        "非所属店の備考",
        ShiftRequestStatus.PENDING);

    ResponseEntity<String> res = getHistoryRaw(castToken);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody())
        .as("授権外の実データ（別キャストの希望・非所属店舗の希望）が生ボディに現れないこと")
        .doesNotContain(canaryNote)
        .doesNotContain(foreignStoreName);
  }

  @Test
  @DisplayName("店 A の inbox に店 B の出勤希望が現れないこと(storeFilter, 正向対照つき)")
  void storeInbox_neverLeaksOtherStoreRequests() {
    String ownNote = "店A inbox正向対照_" + UUID.randomUUID();
    saveShiftRequest(
        myCastId,
        STORE_A,
        LocalDate.of(2999, 5, 1),
        LocalTime.of(19, 0),
        LocalTime.of(21, 0),
        ownNote,
        ShiftRequestStatus.PENDING);

    String foreignNote = "店B inbox漏洩カナリア_" + UUID.randomUUID();
    String foreignCast = createCast(storeForeignId, "出勤希望IT店B別キャスト", null);
    saveShiftRequest(
        foreignCast,
        storeForeignId,
        LocalDate.of(2999, 5, 1),
        LocalTime.of(10, 0),
        LocalTime.of(12, 0),
        foreignNote,
        ShiftRequestStatus.PENDING);

    ResponseEntity<String> res = getInboxRaw(STORE_A, "PENDING");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody()).as("正向対照: 自店の希望は見えること").contains(ownNote);
    assertThat(res.getBody()).as("負向: 他店の希望は見えないこと").doesNotContain(foreignNote);
  }

  @Test
  @DisplayName("承認で APPROVED に遷移し、確定シフトが本人スケジュールと公開出勤表の双方に反映されること")
  void approve_transitionsToApprovedAndCreatesConfirmedShiftReflectedEverywhere() {
    ResponseEntity<JsonNode> created =
        submit(castToken, submitBody(STORE_A, today(), "18:00:00", "20:00:00", "承認反映確認"));
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = created.getBody().path("id").asString();

    ResponseEntity<JsonNode> approved = approve(STORE_A, id);
    assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(approved.getBody().path("status").asString()).isEqualTo("APPROVED");

    ShiftRequest reloaded = shiftRequestRepository.findById(id).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(ShiftRequestStatus.APPROVED);

    // 本人スケジュール（cast_id 単層自限）に確定シフトとして反映される。
    ResponseEntity<JsonNode> schedule =
        rest.exchange(
            "/platform/me/schedule?from=" + today() + "&to=" + today(),
            HttpMethod.GET,
            new HttpEntity<>(bearer(castToken)),
            JsonNode.class);
    assertThat(schedule.getStatusCode()).isEqualTo(HttpStatus.OK);
    boolean scheduleHasIt = false;
    for (JsonNode node : schedule.getBody()) {
      if ("18:00:00".equals(node.path("start_time").asString())
          && "CONFIRMED".equals(node.path("status").asString())) {
        scheduleHasIt = true;
      }
    }
    assertThat(scheduleHasIt).as("本人スケジュールに確定シフトが反映されること").isTrue();

    // 公開出勤表（本日 CONFIRMED のみ）にも反映される。
    HttpHeaders publicHeaders = new HttpHeaders();
    publicHeaders.set("X-Role", "store");
    publicHeaders.set("X-Store-ID", String.valueOf(STORE_A));
    ResponseEntity<JsonNode> publicShifts =
        rest.exchange(
            "/store/shifts/public",
            HttpMethod.GET,
            new HttpEntity<>(publicHeaders),
            JsonNode.class);
    assertThat(publicShifts.getStatusCode()).isEqualTo(HttpStatus.OK);
    boolean publicHasIt = false;
    for (JsonNode node : publicShifts.getBody()) {
      if (myCastId.equals(node.path("cast_id").asString())
          && "18:00:00".equals(node.path("start_time").asString())) {
        publicHasIt = true;
      }
    }
    assertThat(publicHasIt).as("公開出勤表に確定シフトが反映されること").isTrue();
  }

  @Test
  @DisplayName("却下で DECLINED に遷移し、シフトが作成されないこと")
  void decline_transitionsToDeclinedWithoutCreatingShift() {
    ResponseEntity<JsonNode> created =
        submit(castToken, submitBody(STORE_A, tomorrow(), "10:00:00", "12:00:00", "却下確認"));
    String id = created.getBody().path("id").asString();
    long shiftCountBefore = shiftRepository.count();

    ResponseEntity<JsonNode> declined = decline(STORE_A, id);

    assertThat(declined.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(declined.getBody().path("status").asString()).isEqualTo("DECLINED");
    ShiftRequest reloaded = shiftRequestRepository.findById(id).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(ShiftRequestStatus.DECLINED);
    assertThat(shiftRepository.count()).as("却下ではシフトが作成されないこと").isEqualTo(shiftCountBefore);
  }

  @Test
  @DisplayName("店 B の正規店舗文脈からは店 A の出勤希望を承認・却下できないこと(跨店処理遮断、正向対照つき)")
  void legitimateStoreBContext_cannotActOnStoreARequest() {
    // 店 A・店 B 双方に授権された店長（PERM_SHIFT_MANAGE 保持）でログインし、権限線ではなく
    // 店舗線だけが遮断要因であることを正向対照で分離する。
    String managerToken = platformToken("tanaka.hanako@kizuna.test", PASSWORD);

    ResponseEntity<JsonNode> controlCreated =
        submit(castToken, submitBody(STORE_A, tomorrow(), "16:00:00", "18:00:00", "跨店正向対照"));
    String controlId = controlCreated.getBody().path("id").asString();
    ResponseEntity<JsonNode> controlApproved =
        rest.exchange(
            "/store/shift-requests/" + controlId + "/approval",
            HttpMethod.POST,
            new HttpEntity<>(managerHeaders(managerToken, STORE_A)),
            JsonNode.class);
    assertThat(controlApproved.getStatusCode())
        .as("正向対照: 店 A 文脈なら承認できること")
        .isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> created =
        submit(castToken, submitBody(STORE_A, tomorrow(), "21:00:00", "23:00:00", "跨店処理遮断確認"));
    String id = created.getBody().path("id").asString();
    long shiftCountBefore = shiftRepository.count();

    ResponseEntity<JsonNode> crossApprove =
        rest.exchange(
            "/store/shift-requests/" + id + "/approval",
            HttpMethod.POST,
            new HttpEntity<>(managerHeaders(managerToken, STORE_B)),
            JsonNode.class);
    ResponseEntity<JsonNode> crossDecline =
        rest.exchange(
            "/store/shift-requests/" + id + "/decline",
            HttpMethod.POST,
            new HttpEntity<>(managerHeaders(managerToken, STORE_B)),
            JsonNode.class);

    assertThat(crossApprove.getStatusCode().is4xxClientError()).as("店 B 文脈からの承認は拒否されること").isTrue();
    assertThat(crossDecline.getStatusCode().is4xxClientError()).as("店 B 文脈からの却下は拒否されること").isTrue();
    ShiftRequest reloaded = shiftRequestRepository.findById(id).orElseThrow();
    assertThat(reloaded.getStatus()).as("跨店の処理試行で状態が変わらないこと").isEqualTo(ShiftRequestStatus.PENDING);
    assertThat(shiftRepository.count()).as("跨店の承認試行でシフトが作成されないこと").isEqualTo(shiftCountBefore);
  }

  private HttpHeaders managerHeaders(String token, long storeId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(storeId));
    headers.setBearerAuth(token);
    return headers;
  }

  @Test
  @DisplayName("処理済みの出勤希望への再処理は 400 で拒否されること(二重処理)")
  void reprocessingAlreadyDecidedRequest_isRejected() {
    ResponseEntity<JsonNode> created =
        submit(castToken, submitBody(STORE_A, tomorrow(), "13:00:00", "15:00:00", "二重処理確認"));
    String id = created.getBody().path("id").asString();
    assertThat(approve(STORE_A, id).getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> secondApprove = approve(STORE_A, id);
    assertThat(secondApprove.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<JsonNode> declineAfterApprove = decline(STORE_A, id);
    assertThat(declineAfterApprove.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("同一店舗に本人の档案が複数並存しても提出でき、店舗セレクタ一覧は店舗単位に畳まれること")
  void duplicateProfilesInSameStore_doNotBreakSubmitNorStoreList() {
    // 既存アカウントが同店の招待を複数受諾した状態を再現する（受諾フローに同店唯一性の守衛は無い）。
    createCast(STORE_A, "出勤希望IT本人第二档案", castUserId);

    ResponseEntity<JsonNode> created =
        submit(castToken, submitBody(STORE_A, tomorrow(), "11:00:00", "13:00:00", "複数档案でも提出できる"));
    assertThat(created.getStatusCode()).as("複数档案並存でも提出が 500 にならないこと").isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().path("status").asString()).isEqualTo("PENDING");

    ResponseEntity<JsonNode> stores =
        rest.exchange(
            "/platform/me/stores",
            HttpMethod.GET,
            new HttpEntity<>(bearer(castToken)),
            JsonNode.class);
    assertThat(stores.getStatusCode()).isEqualTo(HttpStatus.OK);
    int storeACount = 0;
    for (JsonNode node : stores.getBody()) {
      if (node.path("store_id").asLong() == STORE_A) {
        storeACount++;
      }
    }
    assertThat(storeACount).as("同一店舗はセレクタ一覧で 1 件に畳まれること").isEqualTo(1);
  }

  @Test
  @DisplayName("ROLE_CAST を持たないユーザーは出勤希望の提出で 403(@PreAuthorize の役割線)")
  void nonCastRole_isRejectedOnSubmit() {
    String staffToken = platformToken(NON_CAST_STAFF_EMAIL, PASSWORD);

    ResponseEntity<JsonNode> res =
        submit(staffToken, submitBody(STORE_A, tomorrow(), "18:00:00", "20:00:00", "権限確認"));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("PERM_SHIFT_MANAGE を持たない CAST ユーザーは店舗側 inbox で 403")
  void nonPermUser_isRejectedOnStoreEndpoints() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(STORE_A));
    headers.setBearerAuth(castToken);

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/shift-requests?status=PENDING",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("開始時刻と終了時刻が同一の提出は 400(境界)")
  void submit_rejectsStartEqualsEnd() {
    ResponseEntity<JsonNode> res =
        submit(castToken, submitBody(STORE_A, tomorrow(), "20:00:00", "20:00:00", "境界確認"));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("過去日の勤務日は 400(境界)")
  void submit_rejectsPastWorkDate() {
    String yesterday = LocalDate.now(ZoneId.of("Asia/Tokyo")).minusDays(1).toString();

    ResponseEntity<JsonNode> res =
        submit(castToken, submitBody(STORE_A, yesterday, "18:00:00", "20:00:00", "境界確認"));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("備考が501文字の提出は 400(境界, Bean Validation)")
  void submit_rejectsNoteOver500Chars() {
    String tooLongNote = "あ".repeat(501);

    ResponseEntity<JsonNode> res =
        submit(castToken, submitBody(STORE_A, tomorrow(), "18:00:00", "20:00:00", tooLongNote));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
