package com.kizuna.shift;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.kizuna.shared.CrossTenantTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
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

    // 読取隔離: tenant B の区間 GET には現れない
    assertThat(rangeContains(TENANT_B, "from=2026-07-10&to=2026-07-10", shiftId)).isFalse();

    // 正向対照: 同一ボディ形式で自テナントの更新は成功する（負向 400 がバリデーション起因でない証明）
    ResponseEntity<JsonNode> ownUpdate =
        rest.exchange(
            "/tenant/shifts/" + shiftId,
            HttpMethod.PUT,
            new HttpEntity<>("{\"status\": \"CONFIRMED\"}", tenantHeaders(TENANT_A)),
            JsonNode.class);
    assertThat(ownUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(ownUpdate.getBody().path("status").asText()).isEqualTo("CONFIRMED");

    // 負向: tenant B は更新できない（ServiceException → 400）
    ResponseEntity<JsonNode> tampered =
        rest.exchange(
            "/tenant/shifts/" + shiftId,
            HttpMethod.PUT,
            new HttpEntity<>("{\"status\": \"TENTATIVE\"}", tenantHeaders(TENANT_B)),
            JsonNode.class);
    assertThat(tampered.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // 負向: tenant B は削除できない（ServiceException → 400）
    ResponseEntity<JsonNode> deleted =
        rest.exchange(
            "/tenant/shifts/" + shiftId,
            HttpMethod.DELETE,
            new HttpEntity<>(tenantHeaders(TENANT_B)),
            JsonNode.class);
    assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

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
    String foreignCastId = createCastAs(TENANT_B, "他店キャスト（作成不可）");

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/tenant/shifts",
            new HttpEntity<>(
                shiftBody(foreignCastId, "2026-07-12", "18:00:00", "23:00:00"),
                tenantHeaders(TENANT_A)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // 作成されていないこと（区間 GET が空）
    ResponseEntity<JsonNode> range =
        rest.exchange(
            "/tenant/shifts?from=2026-07-12&to=2026-07-12",
            HttpMethod.GET,
            new HttpEntity<>(tenantHeaders(TENANT_A)),
            JsonNode.class);
    assertThat(range.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(range.getBody().size()).isEqualTo(0);
  }

  @Test
  @DisplayName("自店シフトを他テナントのキャストに付け替えられず、cast_id が変わらないこと")
  void cannotUpdateShiftToOtherTenantCast() {
    String ownCastId = createCastAs(TENANT_A, "自店キャスト（付替元）");
    String shiftId = createShiftAs(TENANT_A, ownCastId, "2026-07-13", "18:00:00", "23:00:00");
    String foreignCastId = createCastAs(TENANT_B, "他店キャスト（付替先）");

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
}
