package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.ReceptionRoute;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * スタッフ起点の受注のライフサイクル（出生確定 → 編集 → 取消／完了）と、状態別の群読み口を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは ADR 0013 の裁定 — 出生確定 / 受付経路の Web 申請の群の拒否 / 受付担当の省略補完 / 更新契約の拡張項目 / 連絡先の訂正 / 終端状態の凍結 /
 * 理由必須の取消と二度目の拒否 / 受注 1 件を消す口が無いこと / 群読み口の絞り込み・検索・並び替えとカーソルの継続。
 *
 * <p>並行取消の 409 は既存の楽観ロック → 例外写像の機構によるもので、ここでは逐次の 400 だけを固定する（並行の決定性は統合テストでは作れない）。
 */
class OrderLifecycleIT extends CrossStoreTestSupport {

  /** demo シード（seed/05-demo.yaml）の山田次郎（STORE_STAFF・授権店舗 = 店舗1）。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  @Autowired private OrderRepository orderRepository;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("店舗が起こした受注は確定で出生すること")
  void storeOriginatedOrdersAreBornConfirmed() {
    ResponseEntity<JsonNode> created = createOrder(body -> body);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    // 電話口で受けると決めた時点で可否は判断済み。画面上でもう一度確定し直す段は無い
    assertThat(created.getBody().path("status").asString()).isEqualTo("CONFIRMED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"MEMBER_WEB", "GUEST_WEB"})
  @DisplayName("作成で受付経路に Web 申請の経路を指定できないこと")
  void createRejectsEveryWebApplicationReceptionRoute(String route) {
    ResponseEntity<JsonNode> rejected =
        createOrder(body -> body.field("reception_route", "\"" + route + "\""));

    // 広告費と効果集計の根拠になる経路記録が代理入力で偽装されないため
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    // 値が enum に無いだけの解読失敗と区別する。区別しないと、値域が変わった日に守衛が消えても緑のまま残る
    assertThat(rejected.getBody().path("error").asString()).contains("Web 申請");
  }

  @Test
  @DisplayName("受付経路 PHONE は作成で受け付けられること")
  void createAcceptsPhoneAsTheReceptionRoute() {
    ResponseEntity<JsonNode> created =
        createOrder(body -> body.field("reception_route", "\"PHONE\""));

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().path("reception_route").asString()).isEqualTo("PHONE");
  }

  @Test
  @DisplayName("受付担当を省略すると実行者本人が受付担当として記録されること")
  void createFallsBackToTheActorAsReceptionist() {
    ResponseEntity<JsonNode> created = createOrder(body -> body.without("receptionist_id"));

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    // 山田次郎シード自身が受付候補の適格条件を満たすため、本人が入る
    assertThat(created.getBody().path("receptionist_id").asLong()).isEqualTo(SEED_RECEPTIONIST_ID);
  }

  @Test
  @DisplayName("店長が受付担当を省略しても本人が受付担当になること（店長も受付候補の適格条件を満たす）")
  void createFallsBackToTheManagerWhenTheyAreEligible() {
    HttpHeaders manager = managerHeaders(STORE_A);
    String castId = createCast(manager, "店長受付");
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders",
            new HttpEntity<>(
                "{\"business_date\": \"" + LocalDate.now() + "\", \"cast_id\": \"" + castId + "\"}",
                manager),
            JsonNode.class);

    // 適格条件は「当店を授権する ORDER_MANAGE 保持の STAFF」。店長はこれを満たす
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().path("receptionist_id").asLong()).isPositive();
  }

  @Test
  @DisplayName("受付候補でない実行者が受付担当を省略したら明示的に撥ねられること")
  void createRejectsAnOmittedReceptionistForAnIneligibleActor() {
    // HQ 管理者は ORDER_SET_MANAGE で店舗を跨いで受注を起こせるが、受付候補の適格条件
    // （当店を授権する ORDER_MANAGE 保持の STAFF）は満たさない。黙って未設定にせず撥ねる
    String hqToken = login("admin@kizuna.test");
    String castId = createCast(storeHeaders(STORE_A), "HQ 起点");
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(hqToken);

    ResponseEntity<JsonNode> rejected =
        rest.postForEntity(
            "/platform/orders",
            new HttpEntity<>(
                "{\"store_id\": "
                    + STORE_A
                    + ", \"business_date\": \""
                    + LocalDate.now()
                    + "\", \"cast_id\": \""
                    + castId
                    + "\"}",
                headers),
            JsonNode.class);

    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(rejected.getBody().path("error").asString()).contains("受付担当を指定してください");
  }

  @Test
  @DisplayName("HQ 経由の作成にも同じ規則（出生確定・Web 申請の経路の拒否）が効くこと")
  void hqOriginatedOrdersFollowTheSameRules() {
    // 入口によって受注の生まれ方が変わらないこと。HQ は店舗側の作成へ委譲するので規則を共有する
    String hqToken = login("admin@kizuna.test");
    String castId = createCast(storeHeaders(STORE_A), "HQ 出生");
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(hqToken);
    String body =
        "{\"store_id\": "
            + STORE_A
            + ", \"business_date\": \""
            + LocalDate.now()
            + "\", \"cast_id\": \""
            + castId
            + "\", \"receptionist_id\": "
            + SEED_RECEPTIONIST_ID;

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/platform/orders", new HttpEntity<>(body + "}", headers), JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().path("status").asString()).isEqualTo("CONFIRMED");

    for (String route : new String[] {"MEMBER_WEB", "GUEST_WEB"}) {
      ResponseEntity<JsonNode> web =
          rest.postForEntity(
              "/platform/orders",
              new HttpEntity<>(body + ", \"reception_route\": \"" + route + "\"}", headers),
              JsonNode.class);
      assertThat(web.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(web.getBody().path("error").asString()).contains("Web 申請");
    }
  }

  // ==================== 編集の契約（#681） ====================

  @Test
  @DisplayName("営業日・場所・媒体を編集で直せること")
  void updateCoversBusinessDateLocationAndMedia() {
    String orderId = orderId(createOrder(body -> body));
    LocalDate rescheduled = LocalDate.now().plusDays(3);

    ResponseEntity<JsonNode> updated =
        update(
            orderId,
            "{\"business_date\": \""
                + rescheduled
                + "\", \"location_address\": \"中央区銀座 1-2-3\","
                + " \"location_building\": \"グランドホテル 1204\", \"carrier\": \"ドコモ\","
                + " \"media_name\": \"自社サイト\"}");

    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    // 改期を取消＋再登録でやると、取消の記録が雑音で汚れる
    assertThat(updated.getBody().path("business_date").asString())
        .isEqualTo(rescheduled.toString());
    // 更新の応答は作業キューの行の形なので、行に載らない項目は詳細の読み口で確かめる
    JsonNode detail = orderJson(orderId);
    assertThat(detail.path("location_address").asString()).isEqualTo("中央区銀座 1-2-3");
    assertThat(detail.path("location_building").asString()).isEqualTo("グランドホテル 1204");
    assertThat(detail.path("carrier").asString()).isEqualTo("ドコモ");
    assertThat(detail.path("media_name").asString()).isEqualTo("自社サイト");
  }

  @Test
  @DisplayName("列の上限を超える場所・媒体を、理由の分かる 400 で撥ねること")
  void updateRejectsTextsLongerThanTheirColumns() {
    String orderId = orderId(createOrder(body -> body));

    // 契約で撥ねないと、溢れた値が更新時の SQLSTATE 22001 になり 500 で返る — 送り手には
    // 「何が長すぎたのか」も「直せば通るのか」も分からない
    ResponseEntity<JsonNode> rejected =
        update(orderId, "{\"location_address\": \"" + "あ".repeat(501) + "\"}");

    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(rejected.getBody().path("error").asString()).contains("500 文字以内");
    // 上限ちょうどは通す（境界を 1 文字ずらすと正当な入力が拒まれる）
    assertThat(
            update(orderId, "{\"location_address\": \"" + "あ".repeat(500) + "\"}").getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("作成も同じ入力を同じ 400 で撥ねること（更新だけ直すと同じ値が口によって 400 と 500 に割れる）")
  void createRejectsTheSameOverlongTextsAsUpdate() {
    // 作成の住所は派遣先と顧客台帳の 2 つへ入るが、上限はどちらも 500 で一致する
    ResponseEntity<JsonNode> longAddress =
        createOrder(body -> body.field("address", "\"" + "あ".repeat(501) + "\""));
    assertThat(longAddress.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(longAddress.getBody().path("error").asString()).contains("500 文字以内");

    ResponseEntity<JsonNode> longCarrier =
        createOrder(body -> body.field("carrier", "\"" + "あ".repeat(101) + "\""));
    assertThat(longCarrier.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // 台帳だけへ入る項目も同じ扱い。溢れは顧客行の挿入で起きるため、契約で撥ねないと受注の口からは
    // 「受注の作成が 500 で落ちた」としか見えない
    ResponseEntity<JsonNode> longLandmark =
        createOrder(
            body ->
                body.field("phone_number", "\"0901111" + (nonce % 10000) + "\"")
                    .field("landmark", "\"" + "あ".repeat(256) + "\""));
    assertThat(longLandmark.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("顧客の着いていない受注の連絡先を訂正できること")
  void updateCorrectsTheContactOfAnUnlinkedOrder() {
    // 電話番号を送らなければ台帳照合は起きず、録入した連絡先が受注側の写しとして残る
    String orderId = orderId(createOrder(body -> body.field("customer_name", "\"誤記の名前\"")));

    ResponseEntity<JsonNode> corrected =
        update(orderId, "{\"contact_name\": \"正しい名前\", \"contact_phone_number\": \"09099998888\"}");

    assertThat(corrected.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(corrected.getBody().path("contact_name").asString()).isEqualTo("正しい名前");
    assertThat(corrected.getBody().path("contact_phone_number").asString())
        .isEqualTo("09099998888");
  }

  @Test
  @DisplayName("顧客が着いた受注へ連絡先を送ると明示的に撥ねられること")
  void updateRejectsContactCorrectionOnALinkedOrder() {
    String customerId = createCustomer("連絡先訂正拒否");
    String orderId =
        orderId(createOrder(body -> body.field("customer_id", "\"" + customerId + "\"")));

    ResponseEntity<JsonNode> rejected = update(orderId, "{\"contact_name\": \"受注側から書こうとした名前\"}");

    // 黙って捨てると送り手は直ったと誤解したまま台帳の誤記が残る
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(rejected.getBody().path("error").asString()).contains("顧客詳細");
  }

  @Test
  @DisplayName("汎用更新の契約に状態が無いこと")
  void updateContractCarriesNoStatus() {
    String orderId = orderId(createOrder(body -> body));

    // 未知の項目として撥ねられる（fail-on-unknown-properties）。この口に残る合法な遷移は無い
    ResponseEntity<JsonNode> rejected =
        rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.PUT,
            new HttpEntity<>("{\"status\": \"COMPLETED\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(statusOf(orderId)).isEqualTo("CONFIRMED");
  }

  // ==================== 終端状態と取消（#682 / ADR 0013） ====================

  @Test
  @DisplayName("取消は理由・実行者・時刻を記録し、二度目は撥ねられること")
  void cancellationRecordsTheReasonAndRefusesToRepeat() {
    String orderId = orderId(createOrder(body -> body));

    ResponseEntity<JsonNode> cancelled = cancel(orderId, "客都合。当日夕方に体調不良の連絡あり");

    // 取消は結果を読まれない操作なので 204。記録は詳細の読み口で確かめる
    assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    JsonNode after = orderJson(orderId);
    assertThat(after.path("status").asString()).isEqualTo("CANCELLED");
    assertThat(after.path("cancelled_reason").asString()).isEqualTo("客都合。当日夕方に体調不良の連絡あり");
    assertThat(after.path("cancelled_by_name").asString()).isNotBlank();
    assertThat(after.path("cancelled_at").asString()).isNotBlank();

    // 二度目を通すと初回の理由と実行者が黙って上書きされ、理由を必須にした意味が消える
    ResponseEntity<JsonNode> again = cancel(orderId, "二度目の理由");
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(cancelledReasonOf(orderId)).isEqualTo("客都合。当日夕方に体調不良の連絡あり");
  }

  @Test
  @DisplayName("理由の無い取消は撥ねられること")
  void cancellationRequiresAReason() {
    String orderId = orderId(createOrder(body -> body));

    assertThat(cancel(orderId, "   ").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(statusOf(orderId)).isEqualTo("CONFIRMED");
  }

  @Test
  @DisplayName("取消済み・完了済みの受注は編集できないこと")
  void terminalOrdersAreFrozen() {
    String cancelledId = orderId(createOrder(body -> body));
    assertThat(cancel(cancelledId, "凍結の確認").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    String completedId = orderId(createOrder(body -> body));
    assertThat(complete(completedId).getStatusCode()).as("前提: 受注を完了できること").isEqualTo(HttpStatus.OK);

    for (String frozen : List.of(cancelledId, completedId)) {
      ResponseEntity<JsonNode> rejected = update(frozen, "{\"pax\": 9}");
      assertThat(rejected.getStatusCode())
          .as("終端状態の受注 %s が編集を撥ねること", frozen)
          .isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(rejected.getBody().path("error").asString()).contains("完了・取消済み");
    }
  }

  @Test
  @DisplayName("完了済みの受注は取消の専用操作でも取り消せないこと")
  void completedOrdersCannotBeCancelled() {
    String orderId = orderId(createOrder(body -> body));
    assertThat(complete(orderId).getStatusCode()).as("前提: 受注を完了できること").isEqualTo(HttpStatus.OK);

    // 誤って完了した受注の救済にこの操作を広げてはならない（ADR 0013）
    assertThat(cancel(orderId, "誤完了の救済に使おうとした").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(statusOf(orderId)).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("受注 1 件を名指して消す口が存在しないこと")
  void thereIsNoEndpointToDeleteASingleOrder() {
    String orderId = orderId(createOrder(body -> body));

    ResponseEntity<JsonNode> deleted =
        rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.DELETE,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);

    // 理由必須の取消を立てながら無痕の硬削除を残すと、理由の必須が制度上の任意へ落ちる
    assertThat(deleted.getStatusCode().is2xxSuccessful()).isFalse();
    assertThat(statusOf(orderId)).as("撥ねた削除が行を消さないこと").isEqualTo("CONFIRMED");
  }

  // ==================== 群読み口（#683 / 裁定 15） ====================

  @Test
  @DisplayName("作業キューが指定した群だけを返し、終端状態を含まないこと")
  void workQueueReturnsOnlyTheRequestedStatuses() {
    String label = "群読み口-" + nonce;
    String confirmedId = orderId(createOrder(body -> body.field("remarks", "\"" + label + "\"")));
    String cancelledId = orderId(createOrder(body -> body.field("remarks", "\"" + label + "\"")));
    assertThat(cancel(cancelledId, "群の確認").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    List<String> queue = idsOf(workQueue("statuses=CONFIRMED&size=2000"));

    assertThat(queue).contains(confirmedId);
    assertThat(queue).doesNotContain(cancelledId);
  }

  @Test
  @DisplayName("アーカイブが終端状態だけを群ごとに返し、総件数を運ぶこと")
  void archiveReturnsTerminalOrdersWithATotalCount() {
    String cancelledId = orderId(createOrder(body -> body));
    assertThat(cancel(cancelledId, "アーカイブの確認").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<JsonNode> archive =
        rest.exchange(
            "/store/orders/archive?statuses=CANCELLED&size=2000",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(archive.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> ids = new ArrayList<>();
    archive.getBody().path("content").forEach(row -> ids.add(row.path("id").asString()));
    assertThat(ids).contains(cancelledId);
    // 総件数が無いとページャは最終ページを出せない
    assertThat(archive.getBody().path("total_elements").asLong()).isPositive();
  }

  @Test
  @DisplayName("作業キューがお客様名の部分一致と営業日で絞り込めること")
  void workQueueFiltersByCustomerNameAndBusinessDate() {
    String unique = "検索対象" + nonce;
    LocalDate day = LocalDate.now().plusDays(11);
    String target =
        orderId(
            createOrder(
                body ->
                    body.field("customer_name", "\"" + unique + "\"")
                        .field("business_date", "\"" + day + "\"")));
    String other = orderId(createOrder(body -> body.field("customer_name", "\"無関係の客\"")));

    List<String> byName =
        idsOf(workQueue("statuses=CONFIRMED&size=2000&customer_name=" + unique.substring(0, 6)));
    assertThat(byName).containsExactly(target);
    assertThat(byName).doesNotContain(other);

    List<String> byDate = idsOf(workQueue("statuses=CONFIRMED&size=2000&business_date=" + day));
    assertThat(byDate).contains(target).doesNotContain(other);
  }

  @Test
  @DisplayName("台帳にも連絡先にも名の無い会員申請由来の受注を、申請時の名乗りで検索できること")
  void workQueueFindsMemberRequestsByTheirDeclaredName() {
    // 会員行が消えた申請の確定は顧客が着かず、受付で録入する連絡先も持たない。名乗りまで
    // 見ないと、画面に出ている行が検索した途端に消える（呼び名の出所と検索の出所が食い違うため）。
    String declared = "名乗り" + nonce;
    Order request =
        Order.builder()
            .businessDate(LocalDate.now())
            .pax(2)
            .status(OrderStatus.CONFIRMED)
            .receptionRoute(ReceptionRoute.MEMBER_WEB)
            .requesterMemberCode("999999999999")
            .requesterDeclaredName(declared)
            .build();
    request.setStoreId(STORE_A);
    String orderId = orderRepository.save(request).getId();

    JsonNode found = workQueue("statuses=CONFIRMED&size=2000&customer_name=" + declared);
    assertThat(idsOf(found)).contains(orderId);

    // null の項目はキーごと応答から消えるため、欠落は has() で見る（isNull() は欠落に対して偽）
    JsonNode row = orderJson(orderId);
    assertThat(row.has("customer_name")).as("前提: 台帳の顧客名を持たないこと").isFalse();
    assertThat(row.has("contact_name")).as("前提: 録入された連絡先も持たないこと").isFalse();
    // 呼び名を出せなければ、作業キューでは「お客様名なし」としか名乗れない
    assertThat(row.path("requester_declared_name").asString()).isEqualTo(declared);
  }

  @Test
  @DisplayName("並び替えの鍵が未設定の受注も、カーソルで続きを取ると必ず現れること")
  void workQueueCursorReachesRowsWhoseSortKeyIsUnset() {
    // 可空の鍵（人数）を素の列のまま比較すると、未設定の行はカーソルの比較が常に不成立になり
    // 先頭ページ以降そこへ二度と到達できなくなる。未設定を最大へ均すことでその穴を塞ぐ
    String unique = "未設定鍵" + nonce;
    String withPax =
        orderId(
            createOrder(
                body -> body.field("customer_name", "\"" + unique + "\"").field("pax", "2")));
    String withoutPax =
        orderId(
            createOrder(body -> body.field("customer_name", "\"" + unique + "\"").without("pax")));

    String query = "statuses=CONFIRMED&sort_key=PAX&customer_name=" + unique + "&size=1";
    JsonNode first = workQueue(query);
    List<String> collected = new ArrayList<>(idsOf(first));
    String cursor = first.path("next_cursor").asString();
    assertThat(cursor).as("前提: 続きがあること").isNotBlank();

    collected.addAll(idsOf(workQueue(query + "&cursor=" + cursor)));

    // 人数の昇順で未設定は末尾へ回る。1 件ずつ辿っても両方に到達できなければならない
    assertThat(collected).containsExactly(withPax, withoutPax);
  }

  @Test
  @DisplayName("処理で手前の行が消えても、続きを取った受注が飛ばされないこと")
  void workQueueCursorDoesNotSkipRowsAfterTheHeadIsProcessed() {
    String unique = "カーソル継続" + nonce;
    List<String> created = new ArrayList<>();
    for (int pax = 1; pax <= 3; pax++) {
      int value = pax;
      created.add(
          orderId(
              createOrder(
                  body ->
                      body.field("customer_name", "\"" + unique + "\"")
                          .field("pax", String.valueOf(value)))));
    }

    String query = "statuses=CONFIRMED&sort_key=PAX&customer_name=" + unique + "&size=1";
    JsonNode first = workQueue(query);
    assertThat(idsOf(first)).containsExactly(created.get(0));
    String cursor = first.path("next_cursor").asString();

    // 続きを取る前に先頭を処理する。位置を「何件目か」で指していると、ここで後続が繰り上がって
    // 2 件目が飛ばされる
    assertThat(cancel(created.get(0), "カーソル継続の確認").getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(idsOf(workQueue(query + "&cursor=" + cursor))).containsExactly(created.get(1));
  }

  @Test
  @DisplayName("並び替えが昇順・降順とも全群へ同じ鍵で当たること")
  void sortingAppliesTheSameKeyInBothDirections() {
    String unique = "並び替え" + nonce;
    String small =
        orderId(
            createOrder(
                body -> body.field("customer_name", "\"" + unique + "\"").field("pax", "1")));
    String large =
        orderId(
            createOrder(
                body -> body.field("customer_name", "\"" + unique + "\"").field("pax", "9")));

    String base = "statuses=CONFIRMED&sort_key=PAX&customer_name=" + unique + "&size=2000";
    assertThat(idsOf(workQueue(base + "&desc=false"))).containsExactly(small, large);
    assertThat(idsOf(workQueue(base + "&desc=true"))).containsExactly(large, small);
  }

  @Test
  @DisplayName("壊れたカーソルは黙って先頭扱いにせず撥ねること")
  void workQueueRejectsAMalformedCursor() {
    // 先頭から取り直した結果を返すと、続きを求めた呼出側には取りこぼしが成功に見える
    ResponseEntity<JsonNode> rejected =
        rest.exchange(
            "/store/orders/work-queue?statuses=CONFIRMED&cursor=not-a-cursor",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // ==================== 受注の用意 ====================

  /** 作成要求の本体。既定は最小構成で、テストが必要な項目だけを足し引きする。 */
  private record OrderBody(List<String> fields) {

    OrderBody field(String name, String rawJsonValue) {
      List<String> next = new ArrayList<>(without(name).fields());
      next.add("\"" + name + "\": " + rawJsonValue);
      return new OrderBody(next);
    }

    OrderBody without(String name) {
      return new OrderBody(
          fields.stream().filter(f -> !f.startsWith("\"" + name + "\":")).toList());
    }

    String toJson() {
      return "{" + String.join(", ", fields) + "}";
    }
  }

  private ResponseEntity<JsonNode> createOrder(java.util.function.UnaryOperator<OrderBody> shape) {
    HttpHeaders headers = storeHeaders(STORE_A);
    String castId = createCast(headers, "ライフサイクル");
    OrderBody base =
        new OrderBody(
            List.of(
                "\"receptionist_id\": " + SEED_RECEPTIONIST_ID,
                "\"business_date\": \"" + LocalDate.now() + "\"",
                "\"cast_id\": \"" + castId + "\""));
    return rest.postForEntity(
        "/store/orders", new HttpEntity<>(shape.apply(base).toJson(), headers), JsonNode.class);
  }

  private String orderId(ResponseEntity<JsonNode> created) {
    assertThat(created.getStatusCode()).as("前提: 受注作成が成功すること").isEqualTo(HttpStatus.CREATED);
    return created.getBody().path("id").asString();
  }

  /** 受注を部分更新する。指名と受付担当は既存の契約が「設定済みなら省略で外せない」と定めているため、 出生時に両方が埋まっているこの経路の受注では毎回運ぶ必要がある。 */
  private ResponseEntity<JsonNode> update(String orderId, String body) {
    JsonNode current = orderJson(orderId);
    String carried =
        "\"receptionist_id\": "
            + current.path("receptionist_id").asLong()
            + ", \"cast_id\": \""
            + current.path("cast_id").asString()
            + "\"";
    String merged = "{" + carried + (body.equals("{}") ? "" : ", " + body.substring(1));
    return rest.exchange(
        "/store/orders/" + orderId,
        HttpMethod.PUT,
        new HttpEntity<>(merged, storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> cancel(String orderId, String reason) {
    return rest.exchange(
        "/store/orders/" + orderId + "/cancellation",
        HttpMethod.POST,
        new HttpEntity<>("{\"reason\": \"" + reason + "\"}", storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> complete(String orderId) {
    return rest.exchange(
        "/store/orders/" + orderId + "/completion",
        HttpMethod.POST,
        new HttpEntity<>(
            "{\"expected_version\":"
                + orderVersion(storeHeaders(STORE_A), orderId)
                + ",\"fee_lines\":[{\"kind\":\"SURCHARGE\",\"name\":\"会計\",\"amount\":12000}]}",
            storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private JsonNode workQueue(String query) {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/orders/work-queue?" + query,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 作業キューを読めること（%s）", query).isEqualTo(HttpStatus.OK);
    return res.getBody();
  }

  private static List<String> idsOf(JsonNode cursorPage) {
    List<String> ids = new ArrayList<>();
    cursorPage.path("content").forEach(row -> ids.add(row.path("id").asString()));
    return ids;
  }

  private String statusOf(String orderId) {
    return orderJson(orderId).path("status").asString();
  }

  private String cancelledReasonOf(String orderId) {
    return orderJson(orderId).path("cancelled_reason").asString();
  }

  private JsonNode orderJson(String orderId) {
    return rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class)
        .getBody();
  }

  private String createCast(HttpHeaders headers, String label) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + label + "-" + System.nanoTime() + "\"}", headers),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: キャスト作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private String createCustomer(String label) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>("{\"name\": \"" + label + "-" + nonce + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 顧客作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }
}
