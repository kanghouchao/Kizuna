package com.kizuna.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMerge;
import com.kizuna.customer.domain.CustomerMergeRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 顧客統合を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは ADR 0010 の骨格 — 存続行へ受注（全状態）と関連（ACTIVE・RELEASED の全区間）が移り、被統合行は削除されずに
 * 統合先参照を持つ墓標として残り、連鎖統合では既存の墓標も新しい統合先へ付け替わる（解決は常に一跳）。統合履歴に誰が・いつ・何を移したかが残り、 取り消す端点は存在しない。
 *
 * <p>統合がポイント台帳・会員の来店履歴に波及しないことは、件数ではなく応答の**内容**を統合の前後で突き合わせて見る。
 * 不一致が無いことは正しさの証明ではないが、内容の同一は構造的な不波及（仕訳も帰属記録も顧客を参照しない）が 実際に成り立っていることの観測になる。
 *
 * <p>統合は店長権限（{@code CUSTOMER_MERGE}）を要するため、基底クラスの店員トークン（山田次郎）ではなく {@link
 * #managerHeaders(long)}（田中花子・店舗{1,2} 授権）で叩く。店員では 403 になることも併せて見る。
 *
 * <p>統合後の読み書きは非対称になる。墓標は一覧にも電話照合にも現れない一方、旧 ID を渡された解決は統合先へ届き、
 * 墓標そのものを名指した書き換え（更新・削除・ポイント調整・解除）は案内の読める 409 になる。
 *
 * <p>重複候補の読み口も同じ非対称の側にある — 見るのは生きた行だけで、統合を 1 件済ませるとその番号は候補から落ちる。候補は店舗の台帳ぜんたいを
 * 走査するため、他のテストが起こした行も一緒に返る。断言はこの実行だけの電話番号で絞ってから行う。
 *
 * <p>応答に出ない事実（統合先参照・統合履歴・付替え後の受注の顧客）は、行を直接読んで固定する。
 */
class CustomerMergeIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** demo シード（seed/05-demo.yaml）の山田次郎（STORE_STAFF・授権店舗 = 店舗1）。受注の受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  private static final String MANAGER_EMAIL = "tanaka.hanako@kizuna.test";

  /** 字面セグメントなので、{@code /store/customers/{id}} ではなくこの読み口へ届くこと自体が経路の断言になる。 */
  private static final String DUPLICATES_PATH = "/store/customers/duplicates";

  private static final int TOTAL_FEE = 12000;

  /** 行を並べるグループの大きさの上限（{@code CustomerService} の同名の定数と揃える）。 */
  private static final int MAX_LISTED_GROUP_SIZE = 20;

  /** 字面セグメントなので、{@code /store/customers/{id}} ではなくこの読み口へ届くこと自体が経路の断言になる。 */
  private static final String COMPARISON_PATH = "/store/customers/merge-comparison";

  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerMemberLinkRepository customerMemberLinkRepository;
  @Autowired private CustomerMergeRepository customerMergeRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;
  @PersistenceContext private EntityManager entityManager;

  private final long nonce = System.nanoTime();

  // ==================== 付替え ====================

  @Test
  @DisplayName("被統合行の受注が状態を問わず存続行に着き、被統合行は墓標として残ること")
  void movesOrdersOfEveryStatusAndLeavesATombstone() {
    String surviving = createCustomer("存続-" + nonce);
    String merged = createCustomer("被統合-" + nonce);
    // 未確定（CREATED）は会員申請だけが持つ状態になったため、店舗の作成経路では作れない。
    // 統合が状態を条件にしないことは、店舗が到達できる 3 状態で固定する。
    String confirmed = confirmedOrderFor(merged, "確定");
    String completed = completedOrderFor(merged, "完了");
    String cancelled = cancelledOrderFor(merged, "取消");

    ResponseEntity<JsonNode> response = merge(STORE_A, surviving, merged);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().path("surviving_customer_id").asString()).isEqualTo(surviving);
    assertThat(response.getBody().path("moved_order_count").asInt()).isEqualTo(3);
    // 状態は付替えの条件に入らない。売上も履歴も欠けないこと。
    assertThat(statusOf(confirmed)).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(statusOf(completed)).isEqualTo(OrderStatus.COMPLETED);
    assertThat(statusOf(cancelled)).isEqualTo(OrderStatus.CANCELLED);
    assertThat(List.of(confirmed, completed, cancelled))
        .allSatisfy(orderId -> assertThat(customerOf(orderId)).isEqualTo(surviving));
    // 行は削除されず、統合先を指す墓標として残る
    assertThat(mergedIntoOf(merged)).isEqualTo(surviving);
    assertThat(mergedIntoOf(surviving)).as("存続行は生きたまま").isNull();
  }

  @Test
  @DisplayName("被統合行の関連が ACTIVE・RELEASED の全区間ごと存続行へ移ること")
  void movesEveryLinkIntervalIncludingReleasedOnes() {
    String surviving = createCustomer("関連存続-" + nonce);
    String merged = createCustomer("関連被統合-" + nonce);
    link(merged, registerMember("merge-link-old"));
    unlink(merged);
    link(merged, registerMember("merge-link-new"));

    ResponseEntity<JsonNode> response = merge(STORE_A, surviving, merged);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().path("moved_link_count").asInt()).isEqualTo(2);
    // 解除済みの区間も移らなければ「過去に誰と紐づいていたか」の履歴が統合で切れる
    assertThat(customerMemberLinkRepository.findHistory(merged, Limit.unlimited())).isEmpty();
    assertThat(customerMemberLinkRepository.findHistory(surviving, Limit.unlimited())).hasSize(2);
    assertThat(customerMemberLinkRepository.findByCustomerIdAndStatus(surviving, LinkStatus.ACTIVE))
        .as("有効な区間は存続行の側で有効なまま")
        .isPresent();
  }

  @Test
  @DisplayName("連鎖統合で既存の墓標も新しい統合先へ付け替わり、旧 ID の解決が一跳で届くこと")
  void flattensChainsSoResolutionIsAlwaysOneHop() {
    String a = createCustomer("連鎖A-" + nonce);
    String b = createCustomer("連鎖B-" + nonce);
    String c = createCustomer("連鎖C-" + nonce);

    assertThat(merge(STORE_A, b, a).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(merge(STORE_A, c, b).getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(mergedIntoOf(a)).as("A→B→C の後、A は直接 C を指す").isEqualTo(c);
    assertThat(mergedIntoOf(b)).isEqualTo(c);
  }

  @Test
  @DisplayName("付替えと圧平が行の版を上げること（並行して読まれていた行の書き戻しが静かに勝たない）")
  void advancesTheVersionOfEveryRowItRewrites() {
    String a = createCustomer("版A-" + nonce);
    String b = createCustomer("版B-" + nonce);
    String c = createCustomer("版C-" + nonce);
    String orderId = orderFor(b, "版受注");
    assertThat(merge(STORE_A, b, a).getStatusCode())
        .as("前提: A を B へ統合できること")
        .isEqualTo(HttpStatus.OK);
    long tombstoneVersionBefore = versionOfCustomer(a);
    long orderVersionBefore = versionOfOrder(orderId);

    assertThat(merge(STORE_A, c, b).getStatusCode()).isEqualTo(HttpStatus.OK);

    // 版を上げないと、統合の前に読まれていた行を後から保存する経路が、自分が読んだ時点の値
    // （墓標 A なら統合先 B、受注なら顧客 B）を他の項目と一緒に書き戻せてしまう。楽観ロックの
    // 述語が成立するため競合として現れず、圧平と付替えだけが静かに取り消される。
    // 外形（並行する 2 つの要求のどちらが勝つか）の固定は後続の並行テストの範囲で、
    // ここではその競合が検出可能になる前提だけを決定的に押さえる。
    assertThat(versionOfCustomer(a)).as("圧平した墓標の版").isGreaterThan(tombstoneVersionBefore);
    assertThat(versionOfOrder(orderId)).as("付け替えた受注の版").isGreaterThan(orderVersionBefore);
  }

  // ==================== 墓標の除外 ====================

  @Test
  @DisplayName("統合後、墓標が顧客一覧に出ないこと")
  void keepsTombstonesOutOfTheCustomerList() {
    String marker = "一覧" + nonce;
    String surviving = createCustomer(marker + "-存続");
    String merged = createCustomer(marker + "-被統合");
    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> listed =
        rest.exchange(
            "/store/customers?search=" + marker,
            HttpMethod.GET,
            new HttpEntity<>(managerHeaders(STORE_A)),
            JsonNode.class);

    assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
    // 「統合済みも表示」の切替は設けない。同じ人が二重に並ばないことが統合の目的である
    assertThat(listed.getBody().path("content"))
        .singleElement()
        .satisfies(row -> assertThat(row.path("id").asString()).isEqualTo(surviving));
  }

  @Test
  @DisplayName("統合前は複数一致で顧客未設定に落ちていた番号が、統合後は存続行に着くこと")
  void collapsesAMultipleMatchIntoTheSurvivingRow() {
    String phoneNumber = phone("照合");
    String surviving = createCustomerWithPhone("照合存続-" + nonce, phoneNumber);
    String merged = createCustomerWithPhone("照合被統合-" + nonce, phoneNumber);

    ResponseEntity<JsonNode> beforeMerge = orderByPhone(phoneNumber, "照合前");
    assertThat(beforeMerge.getBody().hasNonNull("customer_id"))
        .as("前提: 統合前は複数一致で自動照合を断念すること")
        .isFalse();

    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    // 重複を畳んだ番号が 1 行へ収束する。これが果たされないと統合した意味が無い
    ResponseEntity<JsonNode> afterMerge = orderByPhone(phoneNumber, "照合後");
    assertThat(afterMerge.getBody().path("customer_id").asString()).isEqualTo(surviving);
  }

  @Test
  @DisplayName("第一電話番号が一致する 2 行が候補に出て、見比べる材料（受注件数・紐づけの有無）が並ぶこと")
  void listsDuplicateCandidatesWithTheMaterialNeededToCompareThem() {
    String phoneNumber = phone("候補");
    String withOrder = createCustomerWithPhone("候補甲-" + nonce, phoneNumber);
    String withMember = createCustomerWithPhone("候補乙-" + nonce, phoneNumber);
    confirmedOrderFor(withOrder, "候補受注");
    link(withMember, registerMember("merge-candidate"));

    JsonNode group = duplicateGroup(STORE_A, phoneNumber);

    assertThat(group.path("customers")).hasSize(2);
    // 件数と紐づけが無ければ、どちらに来店が積まれているか判らないまま畳むことになる
    JsonNode orderRow = candidateRow(group, withOrder);
    assertThat(orderRow.path("name").asString()).isEqualTo("候補甲-" + nonce);
    assertThat(orderRow.path("phone_number").asString()).isEqualTo(phoneNumber);
    assertThat(orderRow.path("order_count").asInt()).isEqualTo(1);
    assertThat(orderRow.path("member_linked").asBoolean()).isFalse();
    JsonNode memberRow = candidateRow(group, withMember);
    assertThat(memberRow.path("order_count").asInt()).isZero();
    assertThat(memberRow.path("member_linked").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("統合すると、1 行になった番号が候補から消えること")
  void dropsTombstonesFromTheCandidateList() {
    String phoneNumber = phone("候補墓標");
    String surviving = createCustomerWithPhone("候補墓標存続-" + nonce, phoneNumber);
    String merged = createCustomerWithPhone("候補墓標被統合-" + nonce, phoneNumber);
    assertThat(duplicateGroup(STORE_A, phoneNumber).path("customers")).hasSize(2);

    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    // 墓標が候補に残ると、畳んだはずの重複が候補として出続ける
    assertThat(hasDuplicateGroup(STORE_A, phoneNumber)).isFalse();
  }

  @Test
  @DisplayName("電話番号を持たない行・空欄の行が互いの重複候補にならないこと")
  void neverGroupsRowsWithoutAPhoneNumber() {
    // 会員申請の確定時自動整備が起こすのがこの形（氏名だけ・電話は空）。除かないと、それらが
    // 全部ひとつの巨大な偽グループに畳まれて候補が使えなくなる
    createCustomer("無電話甲-" + nonce);
    createCustomer("無電話乙-" + nonce);
    createCustomerWithPhone("空電話甲-" + nonce, "");
    createCustomerWithPhone("空電話乙-" + nonce, "");
    // 全角空白（U+3000）とタブ。PostgreSQL の trim はどちらも落とさないので、trim による
    // 空判定では素通りして巨大な偽グループになる（日本語の台帳では現実に混ざる）
    createCustomerWithPhone("全角空白甲-" + nonce, "　");
    createCustomerWithPhone("全角空白乙-" + nonce, "　");
    // JSON の本文へ直に制御文字は置けないため、タブはエスケープのまま送って受け側で復元させる
    createCustomerWithPhone("タブ甲-" + nonce, "\\t");
    createCustomerWithPhone("タブ乙-" + nonce, "\\t");
    // 番号のある重複も一組起こす。走査が空振りしたまま「空欄のグループは無い」が
    // 真になる（空集合には何でも成り立つ）のを防ぐ対照
    String phoneNumber = phone("無電話対照");
    createCustomerWithPhone("対照甲-" + nonce, phoneNumber);
    createCustomerWithPhone("対照乙-" + nonce, phoneNumber);

    assertThat(duplicateGroups(STORE_A))
        .anySatisfy(
            group -> assertThat(group.path("phone_number").asString()).isEqualTo(phoneNumber))
        .allSatisfy(group -> assertThat(group.path("phone_number").asString()).isNotBlank());
  }

  @Test
  @DisplayName("他店舗で同じ番号が重複していても、当店の候補には出ないこと")
  void keepsForeignStoreDuplicatesOutOfTheCandidateList() {
    String phoneNumber = phone("候補越境");
    createCustomerWithPhoneAt(STORE_B, "候補越境甲-" + nonce, phoneNumber);
    createCustomerWithPhoneAt(STORE_B, "候補越境乙-" + nonce, phoneNumber);

    assertThat(hasDuplicateGroup(STORE_A, phoneNumber)).isFalse();
    assertThat(hasDuplicateGroup(STORE_B, phoneNumber)).as("前提: 起こした重複はその店舗では候補になること").isTrue();
  }

  @Test
  @DisplayName("続きを辿ると、1 ページ目に載らなかったグループへ到達できること")
  void reachesGroupsBeyondTheFirstPageThroughTheCursor() {
    // 番号の昇順で並ぶので、接頭辞を共有させて隣り合わせる
    String prefix = "0119" + Math.abs(("到達" + nonce).hashCode());
    String first = prefix + "1";
    String second = prefix + "2";
    createCustomerWithPhone("到達甲-" + nonce, first);
    createCustomerWithPhone("到達乙-" + nonce, first);
    createCustomerWithPhone("到達丙-" + nonce, second);
    createCustomerWithPhone("到達丁-" + nonce, second);

    // 1 件ずつ辿る。上限で切って黙る形だと、番号を共有する同伴者のような正当な偽陽性が
    // 先頭側を永久に占めたとき、以降の真の重複が一生画面に出ない
    List<String> reached = new ArrayList<>();
    String cursor = PageCursor.encodeKey(prefix);
    for (int page = 0; page < 2; page++) {
      ResponseEntity<JsonNode> response = duplicatesPage(STORE_A, cursor, 1);
      response
          .getBody()
          .path("content")
          .forEach(g -> reached.add(g.path("phone_number").asString()));
      cursor = nextCursorOf(response.getBody());
    }

    assertThat(reached).containsExactly(first, second);
  }

  @Test
  @DisplayName("壊れた続きの位置は、先頭から取り直さずに要求誤りとして撥ねられること")
  void rejectsAMalformedCursorInsteadOfSilentlyRestarting() {
    ResponseEntity<JsonNode> response =
        rest.exchange(
            DUPLICATES_PATH + "?cursor=%%%",
            HttpMethod.GET,
            new HttpEntity<>(managerHeaders(STORE_A)),
            JsonNode.class);

    // 黙って先頭扱いにすると、続きを求めた呼出側に 1 ページ目が返り取りこぼしが成功に見える
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("桁外れに大きいグループは行を並べず、総数は偽らないこと")
  void omitsTheRowsOfAnOversizedGroupWithoutLyingAboutItsSize() {
    // 移行データの代替値のような、識別の手がかりを持たない番号。字面をいくら絞り込んでも
    // すり抜けるので、グループの大きさで頭打ちにする
    String placeholder = phone("代替値");
    int total = MAX_LISTED_GROUP_SIZE + 3;
    List<String> created = new ArrayList<>();
    for (int i = 0; i < total; i++) {
      created.add(createCustomerWithPhone("代替値" + i + "-" + nonce, placeholder));
    }

    JsonNode group = duplicateGroup(STORE_A, placeholder);

    // 数百行から取り出した先頭の数行は本人を見分ける材料にならず、並べれば「この中から選べ」と読ませる
    assertThat(group.path("customers")).isEmpty();
    // 描いた行数を件数として名乗ると、見えている分がその番号の全部だと読まれる
    assertThat(group.path("total").asInt()).isEqualTo(total);
    // 候補に行が出ない番号でも、顧客一覧で 2 行を選べば見比べて統合できる（入口は候補面だけではない）
    assertThat(comparisonRows(STORE_A, created.get(0), created.get(total - 1)))
        .extracting(row -> row.path("id").asString())
        .containsExactly(created.get(0), created.get(total - 1));
    assertThat(merge(STORE_A, created.get(0), created.get(total - 1)).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("重複候補の読み口は店長権限を要し、スタッフ権限では 403 になること")
  void requiresTheManagerOnlyMergePermissionToReadCandidates() {
    ResponseEntity<JsonNode> asStaff =
        rest.exchange(
            DUPLICATES_PATH,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(asStaff.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  // ==================== 見比べ ====================

  @Test
  @DisplayName("選んだ 2 行が、受注件数と紐づけの有無を添えて選んだ順に返ること")
  void returnsTheComparisonMaterialOfTwoCustomersInTheRequestedOrder() {
    String withOrder = createCustomer("見比べ受注-" + nonce);
    String withMember = createCustomer("見比べ会員-" + nonce);
    confirmedOrderFor(withOrder, "見比べ");
    link(withMember, registerMember("comparison"));

    List<JsonNode> rows = comparisonRows(STORE_A, withMember, withOrder);

    // 並びが入れ替わると画面の左右が入れ替わり、「残す行」の選択が別人を指す
    assertThat(rows)
        .extracting(row -> row.path("id").asString())
        .containsExactly(withMember, withOrder);
    // 材料が無いと人手の確認が形だけになる
    assertThat(rows.get(0).path("member_linked").asBoolean()).isTrue();
    assertThat(rows.get(0).path("order_count").asInt()).isZero();
    assertThat(rows.get(1).path("member_linked").asBoolean()).isFalse();
    assertThat(rows.get(1).path("order_count").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName("統合済みの行を含む見比べは 404 になること")
  void refusesToCompareATombstone() {
    String surviving = createCustomer("見比べ存続-" + nonce);
    String merged = createCustomer("見比べ被統合-" + nonce);
    String other = createCustomer("見比べ第三-" + nonce);
    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    // 旧 ID を統合先へ解決する詳細の読み口とは非対称。ここで統合先へ向け直すと、
    // 人が選んだ行とは別の行が並び、同一人物かの判断が別の 2 行についてのものになる
    assertThat(comparisonResponse(STORE_A, other, merged).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("他店舗の顧客を混ぜた見比べは 404 で、その行の内容も返らないこと")
  void keepsForeignStoreCustomersOutOfTheComparison() {
    String foreignName = "見比べ越境-" + nonce;
    String foreignFirst = createCustomerAt(STORE_B, foreignName);
    String foreignSecond = createCustomerAt(STORE_B, "見比べ越境対照-" + nonce);
    String mine = createCustomer("見比べ自店-" + nonce);

    ResponseEntity<JsonNode> response = comparisonResponse(STORE_A, mine, foreignFirst);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().toString()).doesNotContain(foreignName);
    // 空集合には何でも成り立つ。同じ 2 行がその店舗からは読めることを対照に置く
    assertThat(comparisonRows(STORE_B, foreignFirst, foreignSecond))
        .extracting(row -> row.path("name").asString())
        .contains(foreignName);
  }

  @Test
  @DisplayName("見比べの読み口は店長権限を要し、スタッフ権限では 403 になること")
  void requiresTheManagerOnlyMergePermissionToCompare() {
    String first = createCustomer("見比べ権限甲-" + nonce);
    String second = createCustomer("見比べ権限乙-" + nonce);

    ResponseEntity<JsonNode> asStaff =
        rest.exchange(
            comparisonPath(first, second),
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(asStaff.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  // ==================== 旧 ID の解決 ====================

  @Test
  @DisplayName("詳細を旧 ID で取得すると統合先の行が返り、統合済みであることと元の ID が判ること")
  void resolvesADetailRequestedByTheOldIdToTheSurvivingRow() {
    String surviving = createCustomer("詳細存続-" + nonce);
    String merged = createCustomer("詳細被統合-" + nonce);
    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> byOldId = getCustomer(merged);

    // 3xx にはしない。HTTP クライアントが透過的に追随すると、画面が統合の発生を知れなくなる
    assertThat(byOldId.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(byOldId.getBody().path("id").asString()).isEqualTo(surviving);
    assertThat(byOldId.getBody().path("name").asString()).isEqualTo("詳細存続-" + nonce);
    assertThat(byOldId.getBody().path("merged").asBoolean()).isTrue();
    assertThat(byOldId.getBody().path("merged_from_id").asString()).isEqualTo(merged);

    ResponseEntity<JsonNode> live = getCustomer(surviving);
    assertThat(live.getBody().has("merged")).as("生きた行には標識の欄そのものが現れないこと").isFalse();
  }

  @Test
  @DisplayName("受注の顧客指定に旧 ID を渡すと、受注が存続行に着くこと")
  void landsAnOrderOnTheSurvivingRowWhenGivenTheOldCustomerId() {
    String surviving = createCustomer("受注存続-" + nonce);
    String merged = createCustomer("受注被統合-" + nonce);
    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    String orderId = orderFor(merged, "旧ID指定");

    assertThat(customerOf(orderId)).isEqualTo(surviving);
    assertThat(ordersOn(merged)).as("墓標に着いた受注が無いこと").isZero();
  }

  @Test
  @DisplayName("関連の成立先に旧 ID を渡すと、存続行に対して関連が成立すること")
  void establishesTheLinkOnTheSurvivingRowWhenGivenTheOldCustomerId() {
    String surviving = createCustomer("関連解決存続-" + nonce);
    String merged = createCustomer("関連解決被統合-" + nonce);
    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    link(merged, registerMember("merge-resolve-link"));

    assertThat(customerMemberLinkRepository.findByCustomerIdAndStatus(surviving, LinkStatus.ACTIVE))
        .as("会員が死んだ行へ紐づかないこと")
        .isPresent();
    assertThat(linksOn(merged)).isZero();
  }

  @Test
  @DisplayName("墓標そのものへの更新・削除・ポイント調整・解除は、統合先を編集することが判る 409 になること")
  void refusesEveryWriteAimedAtTheTombstoneItself() {
    String surviving = createCustomer("拒否存続-" + nonce);
    String merged = createCustomer("拒否被統合-" + nonce);
    link(surviving, registerMember("merge-refusal"));
    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    List<ResponseEntity<JsonNode>> refusals =
        List.of(
            updateCustomer(merged, "書き換え-" + nonce),
            deleteCustomer(merged),
            adjustPoints(merged),
            unlinkResponse(merged));

    assertThat(refusals)
        .allSatisfy(
            refusal -> {
              assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(refusal.getBody().path("error").asString()).contains("統合先の顧客を編集");
            });
    // ポイント調整と解除は、統合で関連が存続行へ移っているぶん「紐づいていない」の分岐に落ちやすい。
    // 統合済みであることが伝わらなければ、利用者は次に何をすればよいか判らない
    assertThat(customer(merged).getName()).as("撥ねた要求は何も書き換えないこと").isEqualTo("拒否被統合-" + nonce);
    assertThat(customerMemberLinkRepository.findByCustomerIdAndStatus(surviving, LinkStatus.ACTIVE))
        .isPresent();
  }

  // ==================== 統合履歴 ====================

  @Test
  @DisplayName("統合履歴に実行者・実行時刻・存続行・被統合行・移した件数が残り、取り消す端点が存在しないこと")
  void recordsWhoMovedWhatAndOffersNoUndo() {
    String surviving = createCustomer("履歴存続-" + nonce);
    String merged = createCustomer("履歴被統合-" + nonce);
    orderFor(merged, "履歴受注1");
    orderFor(merged, "履歴受注2");
    link(merged, registerMember("merge-history"));

    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    List<CustomerMerge> history = customerMergeRepository.findByMergedCustomerId(merged);
    assertThat(history).hasSize(1);
    CustomerMerge recorded = history.get(0);
    assertThat(recorded.getSurvivingCustomerId()).isEqualTo(surviving);
    assertThat(recorded.getMergedBy()).isEqualTo(managerId());
    assertThat(recorded.getMergedAt()).isNotNull();
    assertThat(recorded.getStoreId()).isEqualTo(STORE_A);
    assertThat(recorded.getMovedOrderCount()).isEqualTo(2);
    assertThat(recorded.getMovedLinkCount()).isEqualTo(1);

    // 統合に undo は無い。誤統合の修復は履歴を根拠とする人手作業である（ADR 0010）。
    // 「取り消せない」は応答符号の字面ではなく、取消を試みた後も墓標と履歴が動かないことで見る
    // （端点が無いときの符号は全域ハンドラの分類次第で変わりうるが、この不変量は変わらない）。
    ResponseEntity<JsonNode> undoAttempt =
        rest.exchange(
            mergePath(surviving),
            HttpMethod.DELETE,
            new HttpEntity<>(managerHeaders(STORE_A)),
            JsonNode.class);

    assertThat(undoAttempt.getStatusCode().is2xxSuccessful()).isFalse();
    assertThat(mergedIntoOf(merged)).as("取消の試みで墓標が生き返らないこと").isEqualTo(surviving);
    assertThat(customerMergeRepository.findByMergedCustomerId(merged)).hasSize(1);
  }

  @Test
  @DisplayName("統合履歴が両方向で読め、実行者・実行時刻・相手の行・移した件数が並ぶこと")
  void readsTheHistoryFromBothSidesOfTheMerge() {
    String surviving = createCustomer("履歴読み存続-" + nonce);
    String merged = createCustomer("履歴読み被統合-" + nonce);
    orderFor(merged, "履歴読み受注");
    link(merged, registerMember("merge-history-read"));

    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode fromSurviving = onlyHistoryRow(surviving);
    assertThat(fromSurviving.path("direction").asString()).isEqualTo("SURVIVING");
    assertThat(fromSurviving.path("counterpart_customer_id").asString()).isEqualTo(merged);
    // 相手の名前まで出す。統合は値を合併しないので墓標にも名前が残り、これが「どの行を畳んだか」の根拠になる
    assertThat(fromSurviving.path("counterpart_customer_name").asString())
        .isEqualTo("履歴読み被統合-" + nonce);
    assertThat(fromSurviving.path("merged_by_name").asString()).isEqualTo(managerDisplayName());
    assertThat(fromSurviving.path("merged_at").asString()).isNotBlank();
    assertThat(fromSurviving.path("moved_order_count").asInt()).isEqualTo(1);
    assertThat(fromSurviving.path("moved_link_count").asInt()).isEqualTo(1);

    // 被統合行そのものを名指した読みでは、同じ 1 件が反対向きで現れる
    JsonNode fromMerged = onlyHistoryRow(merged);
    assertThat(fromMerged.path("id").asString()).isEqualTo(fromSurviving.path("id").asString());
    assertThat(fromMerged.path("direction").asString()).isEqualTo("MERGED");
    assertThat(fromMerged.path("counterpart_customer_id").asString()).isEqualTo(surviving);
    assertThat(fromMerged.path("counterpart_customer_name").asString())
        .isEqualTo("履歴読み存続-" + nonce);
  }

  @Test
  @DisplayName("統合の無い顧客の履歴は空で返り、顧客そのものが無い場合の 404 と分かれること")
  void separatesAnEmptyHistoryFromAFailedRead() {
    String neverMerged = createCustomer("履歴無し-" + nonce);

    assertThat(historyPage(STORE_A, neverMerged, null, null).path("content")).isEmpty();
    // 空を 404 に潰すと、呼出側は「統合が無い」と「読めなかった」を区別できない。
    // 404 は顧客そのものが引けないときだけに留める
    assertThat(historyResponse(STORE_A, "0", null, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("実行者が削除済みでも履歴の行は返り、実行者名だけが欠けること")
  void keepsTheRowWhenTheActorIsGone() {
    String surviving = createCustomer("実行者不明存続-" + nonce);
    String merged = createCustomer("実行者不明被統合-" + nonce);
    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    // 利用者の削除は FK が merged_by を NULL にする。ここで見たいのは「実行者が引けない履歴」の
    // 読みなので、他のテストが依存する種の利用者を消さずに同じ状態を直に作る
    jdbcTemplate.update(
        "update t_customer_merges set merged_by = null where merged_customer_id = ?", merged);

    JsonNode row = onlyHistoryRow(surviving);
    assertThat(row.has("merged_by_name")).as("non_null 直列化で欄ごと欠けること").isFalse();
    assertThat(row.path("counterpart_customer_id").asString()).isEqualTo(merged);
    assertThat(row.path("merged_at").asString()).isNotBlank();
  }

  @Test
  @DisplayName("続きを辿ると 1 ページ目に載らなかった統合へ到達でき、両方向の行が重複しないこと")
  void reachesOlderMergesThroughTheCursor() {
    String surviving = createCustomer("履歴頁存続-" + nonce);
    List<String> mergedIds = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      String merged = createCustomer("履歴頁被統合" + i + "-" + nonce);
      assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);
      mergedIds.add(merged);
    }

    // 続きの条件が両方向の述語と正しく結合していないと、2 ページ目に 1 ページ目の行が混ざる
    JsonNode firstPage = historyPage(STORE_A, surviving, null, 2);
    List<String> collected = new ArrayList<>(counterpartsOf(firstPage));
    assertThat(collected).hasSize(2);
    JsonNode secondPage = historyPage(STORE_A, surviving, nextCursorOf(firstPage), 2);
    collected.addAll(counterpartsOf(secondPage));

    assertThat(collected).containsExactlyInAnyOrderElementsOf(mergedIds);
    assertThat(nextCursorOf(secondPage)).as("3 件を取り切ったら続きは無いこと").isNull();
  }

  @Test
  @DisplayName("統合履歴の閲覧は店長権限を要し、スタッフ権限では 403 になること")
  void requiresTheManagerOnlyMergePermissionToReadTheHistory() {
    String surviving = createCustomer("履歴権限存続-" + nonce);
    String merged = createCustomer("履歴権限被統合-" + nonce);
    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> asStaff =
        rest.exchange(
            mergePath(surviving),
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(asStaff.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("他店舗の顧客の統合履歴は 404 になり、存在の有無が漏れないこと")
  void hidesForeignStoreHistoryBehindNotFound() {
    String survivingInB = createCustomerAt(STORE_B, "履歴越境存続-" + nonce);
    String mergedInB = createCustomerAt(STORE_B, "履歴越境被統合-" + nonce);
    assertThat(merge(STORE_B, survivingInB, mergedInB).getStatusCode()).isEqualTo(HttpStatus.OK);

    // 田中花子は両店舗に授権されるためヘッダは通り、越境は storeFilter による 404 として現れる
    assertThat(historyResponse(STORE_A, survivingInB, null, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(historyResponse(STORE_A, mergedInB, null, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ==================== 拒否 ====================

  @Test
  @DisplayName("自分自身への統合は撥ねられること")
  void rejectsMergingACustomerIntoItself() {
    String customerId = createCustomer("自己統合-" + nonce);

    assertThat(merge(STORE_A, customerId, customerId).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("墓標を存続行に指定した統合と、墓標をもう一度統合する要求はいずれも 409 になること")
  void rejectsMergesThatWouldBreakTheOneHopInvariant() {
    String surviving = createCustomer("再統合存続-" + nonce);
    String merged = createCustomer("再統合被統合-" + nonce);
    String another = createCustomer("再統合別-" + nonce);
    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    // 墓標を存続行にすると、そこへ着けた参照の解決が二跳になる
    assertThat(merge(STORE_A, merged, another).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    // 応答喪失後の再送。前提状態が「生きている行 → 墓標」へ遷移済みなので二度目は通らない
    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  @DisplayName("他店舗の顧客を指定した統合は存続行・被統合行のいずれでも 404 になり、存在の有無が漏れないこと")
  void hidesForeignStoreCustomersBehindNotFound() {
    String inStoreA = createCustomer("越境自店-" + nonce);
    String inStoreB = createCustomerAt(STORE_B, "越境他店-" + nonce);

    // 田中花子は両店舗に授権されるためヘッダは通り、越境は storeFilter による 404 として現れる
    assertThat(merge(STORE_A, inStoreA, inStoreB).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(merge(STORE_A, inStoreB, inStoreA).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(mergedIntoOf(inStoreA)).isNull();
    assertThat(mergedIntoOf(inStoreB)).isNull();
  }

  @Test
  @DisplayName("両行が ACTIVE 関連を持つ統合は撥ねられ、先に関連を解除することが応答から判ること")
  void rejectsMergeWhenBothRowsAreClaimedByAMember() {
    String surviving = createCustomer("両認領存続-" + nonce);
    String merged = createCustomer("両認領被統合-" + nonce);
    link(surviving, registerMember("merge-claim-1"));
    link(merged, registerMember("merge-claim-2"));

    ResponseEntity<JsonNode> response = merge(STORE_A, surviving, merged);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().path("error").asString()).contains("先に関連を解除");
    assertThat(mergedIntoOf(merged)).as("撥ねた統合は何も動かさない").isNull();
  }

  @Test
  @DisplayName("統合の実行は店長権限を要し、スタッフ権限では 403 になること")
  void requiresTheManagerOnlyMergePermission() {
    String surviving = createCustomer("権限存続-" + nonce);
    String merged = createCustomer("権限被統合-" + nonce);

    ResponseEntity<JsonNode> asStaff =
        rest.exchange(
            mergePath(surviving),
            HttpMethod.POST,
            new HttpEntity<>(mergeBody(merged), storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(asStaff.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(mergedIntoOf(merged)).isNull();
  }

  @Test
  @DisplayName("統合に関与した行は存続行・被統合行とも削除できず、案内の読める応答になること")
  void keepsBothMergedRowsUndeletable() {
    String surviving = createCustomer("削除存続-" + nonce);
    String merged = createCustomer("削除被統合-" + nonce);
    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> deleteSurviving = deleteCustomer(surviving);
    ResponseEntity<JsonNode> deleteMerged = deleteCustomer(merged);

    assertThat(deleteSurviving.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(deleteSurviving.getBody().path("error").asString()).contains("統合");
    assertThat(deleteMerged.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(deleteMerged.getBody().path("error").asString()).contains("統合");
    assertThat(customerRepository.findById(surviving)).isPresent();
    assertThat(customerRepository.findById(merged)).isPresent();
  }

  // ==================== 不波及 ====================

  @Test
  @DisplayName("統合の前後で、会員のポイント残高・明細行の内容・来店履歴の行の内容が同一であること")
  void leavesTheMembersLedgerAndVisitHistoryUntouched() {
    RegisteredMember member = registerAndLoginMember("merge-untouched");
    String surviving = createCustomer("不波及存続-" + nonce);
    String merged = createCustomer("不波及被統合-" + nonce);
    link(merged, member.memberCode());
    completedOrderFor(merged, "不波及来店");

    JsonNode balanceBefore = memberGet("/platform/me/points/balance", member).getBody();
    JsonNode entriesBefore = memberGet("/platform/me/points/entries", member).getBody();
    JsonNode visitsBefore = memberGet("/platform/me/visits", member).getBody();
    assertThat(balanceBefore.path("balance").asInt()).as("前提: 来店でポイントが積まれること").isPositive();
    assertThat(entriesBefore.path("content")).as("前提: 明細が 1 行以上あること").isNotEmpty();
    assertThat(visitsBefore.path("content")).as("前提: 来店が 1 行以上あること").isNotEmpty();

    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    // 仕訳も帰属記録も受注と会員を参照し、顧客を参照しない（ADR 0006 / 0009）。件数ではなく内容が同一であること。
    assertThat(memberGet("/platform/me/points/balance", member).getBody()).isEqualTo(balanceBefore);
    assertThat(memberGet("/platform/me/points/entries", member).getBody()).isEqualTo(entriesBefore);
    assertThat(memberGet("/platform/me/visits", member).getBody()).isEqualTo(visitsBefore);
  }

  // ==================== 並行 ====================

  @Test
  @DisplayName("統合が墓標化を済ませて保持している間に走った受注録入は、待ってから存続行に着くこと")
  void anOrderCreatedWhileAMergeHoldsTheRowLandsOnTheSurvivingRow() throws Exception {
    // 統合と受注録入を HTTP 2 本の外から整列させる手段が無いので、断言面を配線へ下ろす。
    // 「墓標化を済ませてまだコミットしていない統合」を別トランザクションで作り、電話照合が
    // 統合前の行を掴んだ受注録入がそれを待ってから、統合先へ着くことを固定する。
    String phoneNumber = phone("並行照合");
    String surviving = createCustomer("並行照合存続-" + nonce);
    String tombstone = createCustomerWithPhone("並行照合被統合-" + nonce, phoneNumber);
    String castId = createCast("並行照合キャスト-" + nonce);

    CountDownLatch mergeHeld = new CountDownLatch(1);
    CountDownLatch releaseMerge = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> inFlightMerge =
          pool.submit(() -> holdCustomerRow(tombstone, surviving, mergeHeld, releaseMerge));
      assertThat(mergeHeld.await(30, TimeUnit.SECONDS)).as("前提: 統合が墓標化を済ませて待つこと").isTrue();

      Future<ResponseEntity<JsonNode>> concurrentOrder =
          pool.submit(() -> orderByPhone(phoneNumber, castId, "並行照合"));
      assertThatThrownBy(() -> concurrentOrder.get(3, TimeUnit.SECONDS))
          .as("受注録入は統合の確定を待つこと")
          .isInstanceOf(TimeoutException.class);

      releaseMerge.countDown();
      inFlightMerge.get(30, TimeUnit.SECONDS);

      ResponseEntity<JsonNode> created = concurrentOrder.get(30, TimeUnit.SECONDS);
      // 押さえた実体から統合先を読むと、電話照合が先に載せた古い値（統合先なし）を見て墓標に着く
      assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(created.getBody().path("customer_id").asString()).isEqualTo(surviving);
    } finally {
      releaseMerge.countDown();
      pool.shutdownNow();
    }
    assertThat(ordersOn(tombstone)).as("墓標に着地した受注が 0 件であること").isZero();
  }

  @Test
  @DisplayName("統合が存続行のロックを待っている間に成立した受注は、統合の付替えが拾うこと")
  void anOrderCreatedWhileAMergeWaitsIsPickedUpByTheRepointing() throws Exception {
    // もう一方の順序。統合は顧客 ID の昇順で押さえるので、存続行を小さい方に選ぶと、統合は
    // 被統合行に触れないまま待つ — その隙に電話照合が生きた被統合行を掴む形を決定的に作れる。
    List<String> pair = customerPairInIdOrder("並行受注先");
    String surviving = pair.get(0);
    String merged = pair.get(1);
    String phoneNumber = phone("並行受注先");
    setPhoneNumber(merged, phoneNumber);
    String castId = createCast("並行受注先キャスト-" + nonce);

    CountDownLatch survivingHeld = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    String orderId;
    try {
      Future<?> holder =
          pool.submit(() -> holdCustomerRow(surviving, null, survivingHeld, release));
      assertThat(survivingHeld.await(30, TimeUnit.SECONDS)).as("前提: 存続行が押さえられること").isTrue();

      Future<ResponseEntity<JsonNode>> blockedMerge =
          pool.submit(() -> merge(STORE_A, surviving, merged));
      assertThatThrownBy(() -> blockedMerge.get(3, TimeUnit.SECONDS))
          .as("統合は存続行のロックを待つこと")
          .isInstanceOf(TimeoutException.class);

      ResponseEntity<JsonNode> created = orderByPhone(phoneNumber, castId, "並行受注先");
      orderId = created.getBody().path("id").asString();
      assertThat(created.getBody().path("customer_id").asString())
          .as("前提: 統合の前に成立した受注は、まだ生きている行に着くこと")
          .isEqualTo(merged);

      release.countDown();
      holder.get(30, TimeUnit.SECONDS);
      assertThat(blockedMerge.get(30, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
    assertThat(customerOf(orderId)).isEqualTo(surviving);
    assertThat(ordersOn(merged)).as("墓標に着地した受注が 0 件であること").isZero();
  }

  @Test
  @DisplayName("統合が墓標化を済ませて保持している間に走った関連の成立は、待ってから存続行に着くこと")
  void aLinkEstablishedWhileAMergeHoldsTheRowLandsOnTheSurvivingRow() throws Exception {
    String surviving = createCustomer("並行関連存続-" + nonce);
    String tombstone = createCustomer("並行関連被統合-" + nonce);
    String memberCode = registerMember("merge-race-link");

    CountDownLatch mergeHeld = new CountDownLatch(1);
    CountDownLatch releaseMerge = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> inFlightMerge =
          pool.submit(() -> holdCustomerRow(tombstone, surviving, mergeHeld, releaseMerge));
      assertThat(mergeHeld.await(30, TimeUnit.SECONDS)).as("前提: 統合が墓標化を済ませて待つこと").isTrue();

      Future<ResponseEntity<JsonNode>> concurrentLink =
          pool.submit(() -> linkResponse(tombstone, memberCode));
      assertThatThrownBy(() -> concurrentLink.get(3, TimeUnit.SECONDS))
          .as("関連の成立は統合の確定を待つこと")
          .isInstanceOf(TimeoutException.class);

      releaseMerge.countDown();
      inFlightMerge.get(30, TimeUnit.SECONDS);

      assertThat(concurrentLink.get(30, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
    } finally {
      releaseMerge.countDown();
      pool.shutdownNow();
    }
    assertThat(customerMemberLinkRepository.findByCustomerIdAndStatus(surviving, LinkStatus.ACTIVE))
        .isPresent();
    assertThat(linksOn(tombstone)).as("墓標に着地した関連が 0 件であること").isZero();
  }

  @Test
  @DisplayName("統合先が別の統合に押さえられているときの解決は、待たずにやり直しの判る 409 になること")
  void reportsAConflictInsteadOfWaitingWhenTheMergeTargetIsHeld() throws Exception {
    // 墓標を押さえたまま統合先を待つと、その統合先を更に統合する要求（圧平で墓標の行を押さえる）と
    // 待ちが環になる。追う先は待たずに取り、取れなければ競合として返す。
    String tombstone = createCustomer("競合墓標-" + nonce);
    String surviving = createCustomer("競合存続-" + nonce);
    String memberCode = registerMember("merge-nowait");

    CountDownLatch survivingHeld = new CountDownLatch(1);
    CountDownLatch releaseSurviving = new CountDownLatch(1);
    CountDownLatch tombstoneHeld = new CountDownLatch(1);
    CountDownLatch releaseTombstone = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(3);
    try {
      Future<?> holdSurviving =
          pool.submit(() -> holdCustomerRow(surviving, null, survivingHeld, releaseSurviving));
      assertThat(survivingHeld.await(30, TimeUnit.SECONDS)).as("前提: 統合先が押さえられること").isTrue();
      Future<?> holdTombstone =
          pool.submit(() -> holdCustomerRow(tombstone, surviving, tombstoneHeld, releaseTombstone));
      assertThat(tombstoneHeld.await(30, TimeUnit.SECONDS)).as("前提: 墓標化が確定直前で待つこと").isTrue();

      Future<ResponseEntity<JsonNode>> blocked =
          pool.submit(() -> linkResponse(tombstone, memberCode));
      assertThatThrownBy(() -> blocked.get(3, TimeUnit.SECONDS))
          .as("解決は墓標化の確定を待つこと")
          .isInstanceOf(TimeoutException.class);

      releaseTombstone.countDown();
      holdTombstone.get(30, TimeUnit.SECONDS);

      // 統合先はまだ押さえられたまま。ここで待たずに返ることが、待ちが環にならない根拠になる
      ResponseEntity<JsonNode> refused = blocked.get(30, TimeUnit.SECONDS);
      assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(refused.getBody().path("error").asString()).contains("統合中の顧客です");
      releaseSurviving.countDown();
      holdSurviving.get(30, TimeUnit.SECONDS);
    } finally {
      releaseTombstone.countDown();
      releaseSurviving.countDown();
      pool.shutdownNow();
    }
    assertThat(linksOn(tombstone)).as("墓標に着地した関連が無いこと").isZero();
    assertThat(linksOn(surviving)).as("撥ねた要求は存続行にも何も残さないこと").isZero();
  }

  /**
   * 顧客行を押さえたままコミットせずに待つ。{@code mergeInto} に統合先を渡すと、押さえた行を墓標にしてから待つ —
   * 統合の実行そのものは他のテストが固定するので、ここで再現するのは行の状態と保持だけである。
   */
  private Object holdCustomerRow(
      String customerId, String mergeInto, CountDownLatch held, CountDownLatch release) {
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              Customer row =
                  entityManager.find(Customer.class, customerId, LockModeType.PESSIMISTIC_WRITE);
              if (mergeInto != null) {
                row.mergeInto(mergeInto);
                entityManager.flush();
              }
              held.countDown();
              awaitQuietly(release);
              return null;
            });
  }

  /** 保持側のトランザクション内で待つ。中断はテストの失敗として現れるので、ここでは握り潰さず状態だけ戻す。 */
  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  // ==================== 統合の呼び出し ====================

  private static String mergePath(String survivingCustomerId) {
    return "/store/customers/" + survivingCustomerId + "/merges";
  }

  private static String mergeBody(String mergedCustomerId) {
    return "{\"merged_customer_id\": \"" + mergedCustomerId + "\"}";
  }

  private ResponseEntity<JsonNode> merge(
      long storeId, String survivingCustomerId, String mergedCustomerId) {
    return rest.exchange(
        mergePath(survivingCustomerId),
        HttpMethod.POST,
        new HttpEntity<>(mergeBody(mergedCustomerId), managerHeaders(storeId)),
        JsonNode.class);
  }

  // ==================== 統合履歴の読み ====================

  private ResponseEntity<JsonNode> historyResponse(
      long storeId, String customerId, String cursor, Integer size) {
    String query =
        (cursor == null ? "" : "?cursor=" + cursor)
            + (size == null ? "" : (cursor == null ? "?" : "&") + "size=" + size);
    return rest.exchange(
        mergePath(customerId) + query,
        HttpMethod.GET,
        new HttpEntity<>(managerHeaders(storeId)),
        JsonNode.class);
  }

  private JsonNode historyPage(long storeId, String customerId, String cursor, Integer size) {
    ResponseEntity<JsonNode> response = historyResponse(storeId, customerId, cursor, size);
    assertThat(response.getStatusCode()).as("前提: 統合履歴を読めること").isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  /** 履歴は顧客 ID で絞られるので、他のテストが起こした統合は混ざらない。 */
  private JsonNode onlyHistoryRow(String customerId) {
    JsonNode content = historyPage(STORE_A, customerId, null, null).path("content");
    assertThat(content).as("統合履歴が 1 件だけあること").hasSize(1);
    return content.get(0);
  }

  private static List<String> counterpartsOf(JsonNode page) {
    List<String> counterparts = new ArrayList<>();
    page.path("content")
        .forEach(row -> counterparts.add(row.path("counterpart_customer_id").asString()));
    return counterparts;
  }

  private String managerDisplayName() {
    return platformUserRepository
        .findByEmail(MANAGER_EMAIL)
        .map(PlatformUser::getDisplayName)
        .orElseThrow();
  }

  /**
   * 候補を続きも含めて集める。店舗の台帳ぜんたいを見るので他のテストが起こした行も一緒に返り、この実行の番号が 1 ページ目に載る保証が無い。
   * 断言はどれも「この実行の番号」で絞ってから行う（{@link #phone} が実行ごと・用途ごとに異なる番号を作る）。
   */
  private List<JsonNode> duplicateGroups(long storeId) {
    List<JsonNode> groups = new ArrayList<>();
    String cursor = null;
    // 続きが尽きない実装欠陥で無限に回らないよう、台帳の規模から見て十分な回数で切る
    for (int page = 0; page < 100; page++) {
      ResponseEntity<JsonNode> response = duplicatesPage(storeId, cursor, null);
      response.getBody().path("content").forEach(groups::add);
      cursor = nextCursorOf(response.getBody());
      if (cursor == null) {
        return groups;
      }
    }
    throw new AssertionError("重複候補の続きが尽きない");
  }

  private ResponseEntity<JsonNode> duplicatesPage(long storeId, String cursor, Integer size) {
    String query =
        (cursor == null ? "" : "?cursor=" + cursor)
            + (size == null ? "" : (cursor == null ? "?" : "&") + "size=" + size);
    ResponseEntity<JsonNode> response =
        rest.exchange(
            DUPLICATES_PATH + query,
            HttpMethod.GET,
            new HttpEntity<>(managerHeaders(storeId)),
            JsonNode.class);
    assertThat(response.getStatusCode()).as("前提: 重複候補を読めること").isEqualTo(HttpStatus.OK);
    return response;
  }

  /** 続きの位置。続きが無いときは non_null 直列化により欄ごと現れない。 */
  private static String nextCursorOf(JsonNode body) {
    JsonNode next = body.path("next_cursor");
    return next.isMissingNode() || next.isNull() ? null : next.asString();
  }

  private JsonNode duplicateGroup(long storeId, String phoneNumber) {
    return findDuplicateGroup(storeId, phoneNumber)
        .orElseThrow(() -> new AssertionError("重複候補に " + phoneNumber + " のグループが無い"));
  }

  private boolean hasDuplicateGroup(long storeId, String phoneNumber) {
    return findDuplicateGroup(storeId, phoneNumber).isPresent();
  }

  private Optional<JsonNode> findDuplicateGroup(long storeId, String phoneNumber) {
    for (JsonNode group : duplicateGroups(storeId)) {
      if (phoneNumber.equals(group.path("phone_number").asString())) {
        return Optional.of(group);
      }
    }
    return Optional.empty();
  }

  // ==================== 見比べの読み ====================

  private static String comparisonPath(String first, String second) {
    return COMPARISON_PATH + "?ids=" + first + "&ids=" + second;
  }

  private ResponseEntity<JsonNode> comparisonResponse(long storeId, String first, String second) {
    return rest.exchange(
        comparisonPath(first, second),
        HttpMethod.GET,
        new HttpEntity<>(managerHeaders(storeId)),
        JsonNode.class);
  }

  private List<JsonNode> comparisonRows(long storeId, String first, String second) {
    ResponseEntity<JsonNode> response = comparisonResponse(storeId, first, second);
    assertThat(response.getStatusCode()).as("前提: 見比べを読めること").isEqualTo(HttpStatus.OK);
    List<JsonNode> rows = new ArrayList<>();
    response.getBody().forEach(rows::add);
    return rows;
  }

  private JsonNode candidateRow(JsonNode group, String customerId) {
    for (JsonNode row : group.path("customers")) {
      if (customerId.equals(row.path("id").asString())) {
        return row;
      }
    }
    throw new AssertionError("候補グループに顧客 " + customerId + " が無い");
  }

  // ==================== 行の直読（応答に出ない事実） ====================

  /** 統合先参照。生きている行では null なので、Optional の写像で畳まずに行そのものを取り出して読む。 */
  private String mergedIntoOf(String customerId) {
    return customer(customerId).getMergedIntoId();
  }

  private Customer customer(String customerId) {
    return customerRepository.findById(customerId).orElseThrow();
  }

  private String customerOf(String orderId) {
    return orderRepository.findById(orderId).map(Order::getCustomerId).orElseThrow();
  }

  private long versionOfCustomer(String customerId) {
    return customer(customerId).getVersion();
  }

  private long versionOfOrder(String orderId) {
    return orderRepository.findById(orderId).map(Order::getVersion).orElseThrow();
  }

  private OrderStatus statusOf(String orderId) {
    return orderRepository.findById(orderId).map(Order::getStatus).orElseThrow();
  }

  /** 墓標に着地した参照の件数。行を直接数えるのは、着地の誤りが読み口からは見えないため。 */
  private int ordersOn(String customerId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from t_orders where customer_id = ?", Integer.class, customerId);
  }

  private int linksOn(String customerId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from t_customer_member_links where customer_id = ?",
        Integer.class,
        customerId);
  }

  private Long managerId() {
    return platformUserRepository.findByEmail(MANAGER_EMAIL).map(PlatformUser::getId).orElseThrow();
  }

  // ==================== 顧客・受注・関連の用意 ====================

  private String createCustomer(String name) {
    return createCustomerAt(STORE_A, name);
  }

  private String createCustomerAt(long storeId, String name) {
    return postCustomer(storeId, "{\"name\": \"" + name + "\"}");
  }

  /** 同店同号の重複。電話照合の複数一致は正規に起こりうるので、実行ごとに違う番号で起こす。 */
  private String createCustomerWithPhone(String name, String phoneNumber) {
    return createCustomerWithPhoneAt(STORE_A, name, phoneNumber);
  }

  private String createCustomerWithPhoneAt(long storeId, String name, String phoneNumber) {
    return postCustomer(
        storeId, "{\"name\": \"" + name + "\", \"phone_number\": \"" + phoneNumber + "\"}");
  }

  private String postCustomer(long storeId, String body) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers", new HttpEntity<>(body, managerHeaders(storeId)), JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: store %d での顧客作成が成功すること", storeId)
        .isTrue();
    return created.getBody().path("id").asString();
  }

  /** 顧客 2 行を ID の昇順で返す。統合がどちらを先に押さえるかを決めたいときに使う。 */
  private List<String> customerPairInIdOrder(String label) {
    return List.of(createCustomer(label + "甲-" + nonce), createCustomer(label + "乙-" + nonce))
        .stream()
        .sorted()
        .toList();
  }

  /** 実行ごと・用途ごとに異なる照合キー。列は VARCHAR(50)。 */
  private String phone(String label) {
    return "090" + Math.abs((label + nonce).hashCode()) + nonce;
  }

  private ResponseEntity<JsonNode> getCustomer(String customerId) {
    return rest.exchange(
        "/store/customers/" + customerId,
        HttpMethod.GET,
        new HttpEntity<>(managerHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> updateCustomer(String customerId, String name) {
    return putCustomer(customerId, "{\"name\": \"" + name + "\"}");
  }

  private void setPhoneNumber(String customerId, String phoneNumber) {
    assertThat(
            putCustomer(customerId, "{\"phone_number\": \"" + phoneNumber + "\"}").getStatusCode())
        .as("前提: 電話番号を後から設定できること")
        .isEqualTo(HttpStatus.OK);
  }

  private ResponseEntity<JsonNode> putCustomer(String customerId, String body) {
    return rest.exchange(
        "/store/customers/" + customerId,
        HttpMethod.PUT,
        new HttpEntity<>(body, managerHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> adjustPoints(String customerId) {
    String body =
        "{\"delta\": 100, \"reason\": \"統合検証の調整\", \"idempotency_key\": \"merge-it-"
            + nonce
            + "-"
            + System.nanoTime()
            + "\"}";
    return rest.postForEntity(
        "/store/customers/" + customerId + "/point-adjustments",
        new HttpEntity<>(body, managerHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> deleteCustomer(String customerId) {
    return rest.exchange(
        "/store/customers/" + customerId,
        HttpMethod.DELETE,
        new HttpEntity<>(managerHeaders(STORE_A)),
        JsonNode.class);
  }

  /** 予約中（CREATED）の受注を 1 件作る。 */
  private String orderFor(String customerId, String label) {
    String castId = createCast(label + "-" + nonce);
    String body =
        "{\"receptionist_id\": "
            + SEED_RECEPTIONIST_ID
            + ", \"business_date\": \""
            + LocalDate.now()
            + "\", \"cast_id\": \""
            + castId
            + "\", \"pax\": 2, \"customer_id\": \""
            + customerId
            + "\", \"remarks\": \""
            + label
            + "\"}";
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders", new HttpEntity<>(body, managerHeaders(STORE_A)), JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 受注作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  /** 顧客 ID を指定せず電話番号だけで録入する受注。着地先は台帳の照合が決める。 */
  private ResponseEntity<JsonNode> orderByPhone(String phoneNumber, String label) {
    return orderByPhone(phoneNumber, createCast(label + "-" + nonce), label);
  }

  private ResponseEntity<JsonNode> orderByPhone(
      String phoneNumber, String castId, String customerName) {
    String body =
        "{\"receptionist_id\": "
            + SEED_RECEPTIONIST_ID
            + ", \"business_date\": \""
            + LocalDate.now()
            + "\", \"cast_id\": \""
            + castId
            + "\", \"customer_name\": \""
            + customerName
            + "\", \"phone_number\": \""
            + phoneNumber
            + "\"}";
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders", new HttpEntity<>(body, managerHeaders(STORE_A)), JsonNode.class);
    assertThat(created.getStatusCode()).as("前提: 電話番号での受注録入が成立すること").isEqualTo(HttpStatus.CREATED);
    return created;
  }

  /** 店舗が起こす受注は確定で出生するため、作成だけで確定済みになる。 */
  private String confirmedOrderFor(String customerId, String label) {
    return orderFor(customerId, label);
  }

  private String completedOrderFor(String customerId, String label) {
    String orderId = confirmedOrderFor(customerId, label);
    assertThat(
            rest.exchange(
                    "/store/orders/" + orderId + "/completion",
                    HttpMethod.POST,
                    new HttpEntity<>("{\"total_fee\": " + TOTAL_FEE + "}", managerHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .as("前提: 受注を完了できること")
        .isEqualTo(HttpStatus.OK);
    return orderId;
  }

  private String cancelledOrderFor(String customerId, String label) {
    String orderId = orderFor(customerId, label);
    assertThat(
            rest.exchange(
                    "/store/orders/" + orderId + "/cancellation",
                    HttpMethod.POST,
                    new HttpEntity<>("{\"reason\": \"統合テストの前提づくり\"}", managerHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .as("前提: 受注を取消できること")
        .isEqualTo(HttpStatus.NO_CONTENT);
    return orderId;
  }

  private String createCast(String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: キャスト作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private void link(String customerId, String memberCode) {
    assertThat(linkResponse(customerId, memberCode).getStatusCode())
        .as("前提: 会員の紐づけが成功すること")
        .isEqualTo(HttpStatus.OK);
  }

  private ResponseEntity<JsonNode> linkResponse(String customerId, String memberCode) {
    return rest.exchange(
        "/store/customers/" + customerId + "/member-link",
        HttpMethod.POST,
        new HttpEntity<>("{\"member_code\": \"" + memberCode + "\"}", managerHeaders(STORE_A)),
        JsonNode.class);
  }

  private void unlink(String customerId) {
    assertThat(unlinkResponse(customerId).getStatusCode())
        .as("前提: 会員の紐づけを解除できること")
        .isEqualTo(HttpStatus.NO_CONTENT);
  }

  private ResponseEntity<JsonNode> unlinkResponse(String customerId) {
    return rest.exchange(
        "/store/customers/" + customerId + "/member-link",
        HttpMethod.DELETE,
        new HttpEntity<>(managerHeaders(STORE_A)),
        JsonNode.class);
  }

  // ==================== 会員 ====================

  /** 会員本人として読むための材料。 */
  private record RegisteredMember(String memberCode, String token) {}

  private String registerMember(String prefix) {
    return registerMemberAs(uniqueEmail(prefix)).memberCode();
  }

  private RegisteredMember registerAndLoginMember(String prefix) {
    String email = uniqueEmail(prefix);
    String memberCode = registerMemberAs(email).memberCode();
    ResponseEntity<JsonNode> login =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(login.getStatusCode()).as("前提: 会員としてログインできること").isEqualTo(HttpStatus.OK);
    return new RegisteredMember(memberCode, login.getBody().path("token").asString());
  }

  private RegisteredMember registerMemberAs(String email) {
    ResponseEntity<JsonNode> registered =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + email
                    + "\", \"password\": \""
                    + PASSWORD
                    + "\", \"display_name\": \"統合検証会員\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(registered.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    return new RegisteredMember(registered.getBody().path("member_code").asString(), null);
  }

  private ResponseEntity<JsonNode> memberGet(String path, RegisteredMember member) {
    HttpHeaders headers = jsonHeaders();
    headers.setBearerAuth(member.token());
    ResponseEntity<JsonNode> response =
        rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    assertThat(response.getStatusCode()).as("前提: %s を本人として読めること", path).isEqualTo(HttpStatus.OK);
    return response;
  }

  private String uniqueEmail(String prefix) {
    return prefix + "-merge-it-" + nonce + "-" + System.nanoTime() + "@kizuna.test";
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
