package com.kizuna.shift;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.CrossTenantTestSupport;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.tenant.domain.Tenant;
import com.kizuna.tenant.domain.TenantRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Shift のクロステナント分離を本物の PostgreSQL で検証する統合テスト（issue #278）。
 *
 * <p>CastCrossTenantIT をミラー。tenant A のシフトを tenant B が 区間 GET で閲覧・PUT・DELETE できないことを固定し、 作成時の既定ステータス
 * TENTATIVE と区間 GET ラウンドトリップも合わせて検証する。 シフト API は GET /{id} を持たない（区間 GET のみ）ため、読取隔離は「区間 GET
 * に現れないこと」で確認する。
 */
class ShiftCrossTenantIT extends CrossTenantTestSupport {

  private static final String SHIFTS_PUBLIC = "/tenant/shifts/public";
  private static final String FOREIGN_TENANT_DOMAIN = "shift-it.kizuna.test";

  @Autowired private CastRepository castRepository;

  @Autowired private ShiftRepository shiftRepository;

  @Autowired private TenantRepository tenantRepository;

  /**
   * クロステナント検証用の第二テナントを用意し、採番された実 id を返す（MenuCrossTenantIT と同型）。 シードにはテナント 1 しか無く、定数 {@code
   * TENANT_B}=2 は t_stores に実在しないため、直挿し先の store_id を FK 違反させずに得るには実テナントを find-or-create する必要がある。
   */
  private long ensureForeignTenantId() {
    return tenantRepository
        .findByDomain(FOREIGN_TENANT_DOMAIN)
        .orElseGet(
            () ->
                tenantRepository.save(new Tenant("統合テスト第二テナント（シフト）", FOREIGN_TENANT_DOMAIN, null)))
        .getId();
  }

  /**
   * 他テナントの Cast をリポジトリ直挿しで用意する。 HTTP 経由の作成は tenant A の JWT + 他テナントの X-Tenant-ID が
   * TenantIdInterceptor に 403 で弾かれるため、{@code @TenantScoped} を経由せず storeFilter が無効な
   * リポジトリ直接呼び出しで他テナントのデータを書く（MenuCrossTenantIT と同型）。帰属先は実在する第二テナントの採番 id を使う。
   */
  private String createForeignCast(String name) {
    Cast cast = Cast.builder().name(name).build();
    cast.setStoreId(ensureForeignTenantId());
    return castRepository.save(cast).getId();
  }

  private String createCastAs(long tenantId, String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/tenant/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", tenantHeaders(tenantId)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: tenant %d でのキャスト作成が成功すること", tenantId)
        .isTrue();
    String id = created.getBody().path("id").asText();
    assertThat(id).isNotBlank();
    return id;
  }

  private String shiftBody(String castId, String workDate, String startTime, String endTime) {
    return "{\"cast_id\": \""
        + castId
        + "\", \"work_date\": \""
        + workDate
        + "\", \"start_time\": \""
        + startTime
        + "\", \"end_time\": \""
        + endTime
        + "\"}";
  }

  private String shiftBody(
      String castId, String workDate, String startTime, String endTime, String status) {
    return "{\"cast_id\": \""
        + castId
        + "\", \"work_date\": \""
        + workDate
        + "\", \"start_time\": \""
        + startTime
        + "\", \"end_time\": \""
        + endTime
        + "\", \"status\": \""
        + status
        + "\"}";
  }

  private String createShiftAs(
      long tenantId, String castId, String workDate, String startTime, String endTime) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/tenant/shifts",
            new HttpEntity<>(
                shiftBody(castId, workDate, startTime, endTime), tenantHeaders(tenantId)),
            JsonNode.class);
    assertThat(created.getStatusCode())
        .as("前提: tenant %d でのシフト作成が成功すること", tenantId)
        .isEqualTo(HttpStatus.CREATED);
    return created.getBody().path("id").asText();
  }

  private JsonNode findInRange(long tenantId, String range, String shiftId) {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/tenant/shifts?" + range,
            HttpMethod.GET,
            new HttpEntity<>(tenantHeaders(tenantId)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    for (JsonNode node : res.getBody()) {
      if (shiftId.equals(node.path("id").asText())) {
        return node;
      }
    }
    return null;
  }

  private boolean rangeContains(long tenantId, String range, String shiftId) {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/tenant/shifts?" + range,
            HttpMethod.GET,
            new HttpEntity<>(tenantHeaders(tenantId)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    for (JsonNode node : res.getBody()) {
      if (shiftId.equals(node.path("id").asText())) {
        return true;
      }
    }
    return false;
  }

  @Test
  @DisplayName("シフト作成は既定 TENTATIVE で、区間 GET に作成分が返ること")
  void createDefaultsToTentativeAndListedInRange() {
    String castId = createCastAs(TENANT_A, "統合テストキャスト（作成）");

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/tenant/shifts",
            new HttpEntity<>(
                shiftBody(castId, "2026-07-08", "18:00:00", "23:00:00"), tenantHeaders(TENANT_A)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().path("status").asText())
        .as("既定ステータスは TENTATIVE")
        .isEqualTo("TENTATIVE");
    String shiftId = created.getBody().path("id").asText();
    assertThat(shiftId).isNotBlank();

    // 区間 GET（日=from==to）に作成分が返る
    assertThat(rangeContains(TENANT_A, "from=2026-07-08&to=2026-07-08", shiftId)).isTrue();
  }

  @Test
  @DisplayName("他テナントはシフトを区間 GET で閲覧・更新・削除できず、データも変わらないこと")
  void otherTenantCannotReadUpdateOrDeleteForeignShift() {
    String castId = createCastAs(TENANT_A, "統合テストキャスト（隔離）");
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/tenant/shifts",
            new HttpEntity<>(
                shiftBody(castId, "2026-07-10", "19:00:00", "23:00:00"), tenantHeaders(TENANT_A)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String shiftId = created.getBody().path("id").asText();

    // 読取隔離: tenant A の JWT に X-Tenant-ID: B を詐称した区間 GET は 403 で拒否され、tenant A のシフトに到達できない
    ResponseEntity<JsonNode> foreignRange =
        rest.exchange(
            "/tenant/shifts?from=2026-07-10&to=2026-07-10",
            HttpMethod.GET,
            new HttpEntity<>(tenantHeaders(TENANT_B)),
            JsonNode.class);
    assertThat(foreignRange.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // 正向対照: 同一ボディ形式で自テナントの更新は成功する（負向 403 がバリデーション起因でない証明）
    ResponseEntity<JsonNode> ownUpdate =
        rest.exchange(
            "/tenant/shifts/" + shiftId,
            HttpMethod.PUT,
            new HttpEntity<>("{\"status\": \"CONFIRMED\"}", tenantHeaders(TENANT_A)),
            JsonNode.class);
    assertThat(ownUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(ownUpdate.getBody().path("status").asText()).isEqualTo("CONFIRMED");

    // 負向: tenant B は更新できない（越権はインターセプタが拒否 → 403）
    ResponseEntity<JsonNode> tampered =
        rest.exchange(
            "/tenant/shifts/" + shiftId,
            HttpMethod.PUT,
            new HttpEntity<>("{\"status\": \"TENTATIVE\"}", tenantHeaders(TENANT_B)),
            JsonNode.class);
    assertThat(tampered.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // 負向: tenant B は削除できない（越権はインターセプタが拒否 → 403）
    ResponseEntity<JsonNode> deleted =
        rest.exchange(
            "/tenant/shifts/" + shiftId,
            HttpMethod.DELETE,
            new HttpEntity<>(tenantHeaders(TENANT_B)),
            JsonNode.class);
    assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // データ不変: tenant A からはまだ存在し、status は tenant A が設定した CONFIRMED のまま
    ResponseEntity<JsonNode> after =
        rest.exchange(
            "/tenant/shifts?from=2026-07-10&to=2026-07-10",
            HttpMethod.GET,
            new HttpEntity<>(tenantHeaders(TENANT_A)),
            JsonNode.class);
    assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode found = null;
    for (JsonNode node : after.getBody()) {
      if (shiftId.equals(node.path("id").asText())) {
        found = node;
        break;
      }
    }
    assertThat(found).as("tenant B の操作後も tenant A のシフトは残っていること").isNotNull();
    assertThat(found.path("status").asText()).isEqualTo("CONFIRMED");
  }

  @Test
  @DisplayName("他テナントのキャスト id ではシフトを作成できず、区間 GET にも現れないこと")
  void cannotCreateShiftWithOtherTenantCast() {
    String foreignCastId = createForeignCast("他店キャスト（作成不可）");

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/tenant/shifts",
            new HttpEntity<>(
                shiftBody(foreignCastId, "2026-07-12", "18:00:00", "23:00:00"),
                tenantHeaders(TENANT_A)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // 作成されていないこと（区間 GET に当該 cast_id のシフトが現れない）。
    // 実 DB は実行間で残留し得るため件数一致ではなく「含まない」で判定する。
    ResponseEntity<JsonNode> range =
        rest.exchange(
            "/tenant/shifts?from=2026-07-12&to=2026-07-12",
            HttpMethod.GET,
            new HttpEntity<>(tenantHeaders(TENANT_A)),
            JsonNode.class);
    assertThat(range.getStatusCode()).isEqualTo(HttpStatus.OK);
    boolean containsForeignCast = false;
    for (JsonNode node : range.getBody()) {
      if (foreignCastId.equals(node.path("cast_id").asText())) {
        containsForeignCast = true;
        break;
      }
    }
    assertThat(containsForeignCast).as("拒否されたシフトは作成されていないこと").isFalse();
  }

  @Test
  @DisplayName("自店シフトを他テナントのキャストに付け替えられず、cast_id が変わらないこと")
  void cannotUpdateShiftToOtherTenantCast() {
    String ownCastId = createCastAs(TENANT_A, "自店キャスト（付替元）");
    String shiftId = createShiftAs(TENANT_A, ownCastId, "2026-07-13", "18:00:00", "23:00:00");
    String foreignCastId = createForeignCast("他店キャスト（付替先）");

    ResponseEntity<JsonNode> put =
        rest.exchange(
            "/tenant/shifts/" + shiftId,
            HttpMethod.PUT,
            new HttpEntity<>("{\"cast_id\": \"" + foreignCastId + "\"}", tenantHeaders(TENANT_A)),
            JsonNode.class);
    assertThat(put.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // データ不変: cast_id は自店のキャストのまま
    JsonNode found = findInRange(TENANT_A, "from=2026-07-13&to=2026-07-13", shiftId);
    assertThat(found).isNotNull();
    assertThat(found.path("cast_id").asText()).isEqualTo(ownCastId);
  }

  // ---- 公開出勤表 GET /tenant/shifts/public（issue #279）----

  /** テナント文脈のみ（未認証: X-Role + X-Tenant-ID、Authorization なし）の公開エンドポイント用ヘッダ。 */
  private HttpHeaders publicHeaders(long tenantId) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Role", "tenant");
    headers.set("X-Tenant-ID", String.valueOf(tenantId));
    return headers;
  }

  private ResponseEntity<JsonNode> getPublicShifts(HttpHeaders headers) {
    return rest.exchange(SHIFTS_PUBLIC, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
  }

  /** 指定ステータスのシフトを HTTP で播種する（作成成功を前提として検証する）。 */
  private void seedShift(
      long tenantId,
      String castId,
      String workDate,
      String startTime,
      String endTime,
      String status) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/tenant/shifts",
            new HttpEntity<>(
                shiftBody(castId, workDate, startTime, endTime, status), tenantHeaders(tenantId)),
            JsonNode.class);
    assertThat(created.getStatusCode())
        .as("前提: tenant %d でのシフト播種（%s）が成功すること", tenantId, status)
        .isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().path("status").asText()).as("前提: 播種したシフトのステータス").isEqualTo(status);
  }

  /** 第二テナントの ACTIVE な Cast をリポジトリ直挿しで用意する（公開エンドポイントは ACTIVE のみ結合するため）。 */
  private String createActiveForeignCast(String name, long tenantId) {
    Cast cast = Cast.builder().name(name).status("ACTIVE").build();
    cast.setStoreId(tenantId);
    return castRepository.save(cast).getId();
  }

  /** 第二テナントの本日 CONFIRMED シフトをリポジトリ直挿しで用意する。 */
  private void seedForeignConfirmedShift(
      String castId, LocalDate workDate, LocalTime startTime, LocalTime endTime, long tenantId) {
    Shift shift =
        Shift.builder()
            .castId(castId)
            .workDate(workDate)
            .startTime(startTime)
            .endTime(endTime)
            .status("CONFIRMED")
            .build();
    shift.setStoreId(tenantId);
    shiftRepository.save(shift);
  }

  private int indexOfCastName(JsonNode body, String castName) {
    for (int i = 0; i < body.size(); i++) {
      if (castName.equals(body.get(i).path("cast_name").asText())) {
        return i;
      }
    }
    return -1;
  }

  private JsonNode nodeByCastName(JsonNode body, String castName) {
    for (JsonNode node : body) {
      if (castName.equals(node.path("cast_name").asText())) {
        return node;
      }
    }
    return null;
  }

  @Test
  @DisplayName("公開出勤表は本日 CONFIRMED のみを start_time 昇順で未認証に返し、TENTATIVE・他日は除外すること")
  void publicShiftsReturnsTodayConfirmedInAscendingOrder() {
    String today = LocalDate.now(ZoneId.of("Asia/Tokyo")).toString();
    String prevDay = LocalDate.now(ZoneId.of("Asia/Tokyo")).minusDays(1).toString();
    String nextDay = LocalDate.now(ZoneId.of("Asia/Tokyo")).plusDays(1).toString();
    String suffix = UUID.randomUUID().toString();
    String earlyName = "公開出勤_18時_" + suffix;
    String lateName = "公開出勤_21時_" + suffix;
    String tentativeName = "公開出勤_仮_" + suffix;
    String prevName = "公開出勤_前日_" + suffix;
    String nextName = "公開出勤_翌日_" + suffix;

    String earlyCastId = createCastAs(TENANT_A, earlyName);
    String lateCastId = createCastAs(TENANT_A, lateName);
    String tentativeCastId = createCastAs(TENANT_A, tentativeName);
    String prevCastId = createCastAs(TENANT_A, prevName);
    String nextCastId = createCastAs(TENANT_A, nextName);

    // 遅い開始時刻（21:00）を先に播種し、API 側が start_time 昇順で並べ替えることを検証する。
    seedShift(TENANT_A, lateCastId, today, "21:00:00", "23:00:00", "CONFIRMED");
    seedShift(TENANT_A, earlyCastId, today, "18:00:00", "20:00:00", "CONFIRMED");
    seedShift(TENANT_A, tentativeCastId, today, "12:00:00", "14:00:00", "TENTATIVE");
    seedShift(TENANT_A, prevCastId, prevDay, "18:00:00", "20:00:00", "CONFIRMED");
    seedShift(TENANT_A, nextCastId, nextDay, "18:00:00", "20:00:00", "CONFIRMED");

    // 未認証（Authorization なし）でテナント文脈のみで公開エンドポイントを叩く。
    ResponseEntity<JsonNode> res = getPublicShifts(publicHeaders(TENANT_A));
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = res.getBody();

    // 本日 CONFIRMED の 2 名が現れ、18:00 が 21:00 より前に並ぶ。
    int earlyIdx = indexOfCastName(body, earlyName);
    int lateIdx = indexOfCastName(body, lateName);
    assertThat(earlyIdx).as("18:00 のキャストが応答に含まれること").isGreaterThanOrEqualTo(0);
    assertThat(lateIdx).as("21:00 のキャストが応答に含まれること").isGreaterThanOrEqualTo(0);
    assertThat(earlyIdx).as("start_time 昇順（18:00 が 21:00 より前）").isLessThan(lateIdx);

    // cast_id / start_time / end_time が播種値と一致する。
    JsonNode earlyNode = nodeByCastName(body, earlyName);
    assertThat(earlyNode.path("cast_id").asText()).isEqualTo(earlyCastId);
    assertThat(earlyNode.path("start_time").asText()).isEqualTo("18:00:00");
    assertThat(earlyNode.path("end_time").asText()).isEqualTo("20:00:00");
    JsonNode lateNode = nodeByCastName(body, lateName);
    assertThat(lateNode.path("cast_id").asText()).isEqualTo(lateCastId);
    assertThat(lateNode.path("start_time").asText()).isEqualTo("21:00:00");
    assertThat(lateNode.path("end_time").asText()).isEqualTo("23:00:00");

    // TENTATIVE・前日・翌日は公開されない（キャスト名で不在を確認）。
    assertThat(indexOfCastName(body, tentativeName)).as("TENTATIVE は非公開").isEqualTo(-1);
    assertThat(indexOfCastName(body, prevName)).as("前日分は非公開").isEqualTo(-1);
    assertThat(indexOfCastName(body, nextName)).as("翌日分は非公開").isEqualTo(-1);
  }

  @Test
  @DisplayName("公開出勤表は他テナントの本日 CONFIRMED シフトを漏らさないこと（正向対照つき）")
  void publicShiftsDoesNotLeakOtherTenantData() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
    long foreignTenantId = ensureForeignTenantId();
    String foreignName = "公開出勤_他店_" + UUID.randomUUID();
    String foreignCastId = createActiveForeignCast(foreignName, foreignTenantId);
    seedForeignConfirmedShift(
        foreignCastId, today, LocalTime.of(18, 0), LocalTime.of(20, 0), foreignTenantId);

    // 正向対照: 第二テナントの公開 GET にはその実データが現れる（endpoint が読める＆データが実在する証明）。
    ResponseEntity<JsonNode> foreignTenant = getPublicShifts(publicHeaders(foreignTenantId));
    assertThat(foreignTenant.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(indexOfCastName(foreignTenant.getBody(), foreignName))
        .as("正向対照: 第二テナントでは自店のシフトが見える")
        .isGreaterThanOrEqualTo(0);

    // 負向: tenant A の公開 GET には第二テナントのキャスト名が現れない（実データ非漏洩）。
    ResponseEntity<JsonNode> tenantA = getPublicShifts(publicHeaders(TENANT_A));
    assertThat(tenantA.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(indexOfCastName(tenantA.getBody(), foreignName))
        .as("負向: tenant A には第二テナントのシフトが漏れない")
        .isEqualTo(-1);
  }

  @Test
  @DisplayName("完全匿名（ヘッダ無し）の公開出勤表 GET は 403 で fail-closed になること")
  void anonymousPublicShiftsIsForbidden() {
    ResponseEntity<JsonNode> res = getPublicShifts(new HttpHeaders());
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
