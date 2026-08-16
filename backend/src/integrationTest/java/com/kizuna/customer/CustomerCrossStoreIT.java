package com.kizuna.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * Customer のクロス店舗分離を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>store A が作成した Customer を store B が GET /store/customers/{id}（findById 経由） で読めないことを固定する
 * （applyToLoadByKey=true 回帰）。
 *
 * <p>認証・店舗ヘッダ組立は {@link CrossStoreTestSupport} に共通化する。
 */
class CustomerCrossStoreIT extends CrossStoreTestSupport {

  private String createCustomerAs(long storeId, String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", storeHeaders(storeId)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: store %d での顧客作成が成功すること", storeId)
        .isTrue();
    String id = created.getBody().path("id").asString();
    assertThat(id).isNotBlank();
    return id;
  }

  @Test
  @DisplayName("自店舗で作成した顧客は GET /store/customers/{id} で取得できること")
  void ownStoreCanReadOwnCustomer() {
    String id = createCustomerAs(STORE_A, "統合テスト顧客（正向）");

    ResponseEntity<JsonNode> got =
        rest.exchange(
            "/store/customers/" + id,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(got.getBody().path("id").asString()).isEqualTo(id);
  }

  @Test
  @DisplayName("非授権の店舗文脈からの顧客 ID 直指しはインターセプタが 403 で拒むこと")
  void otherStoreCannotReadForeignCustomerById() {
    String id = createCustomerAs(STORE_A, "統合テスト顧客（負向）");

    ResponseEntity<JsonNode> leaked =
        rest.exchange(
            "/store/customers/" + id,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_B)),
            JsonNode.class);

    // 単店授権のスタッフは JWT と X-Store-ID の不一致でインターセプタに拒まれ、SQL まで届かない。
    // フィルタ層の境界（多店授権者の越境が 404 になること）は StoreScopedLoadByKeyIT が固定する。
    assertThat(leaked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  // "customer-it-likeesc-a_b" は _ をワイルドカードとして扱うと customer-it-likeesc-acb に一致してしまう。
  // 検索語は字面として照合されるべきなので 0 件が正。
  @Test
  @DisplayName("検索語中の LIKE メタ文字は字面として扱われ、ワイルドカードにならないこと")
  void searchTreatsLikeMetacharactersAsLiterals() {
    createCustomerAs(STORE_A, "customer-it-likeesc-acb");

    ResponseEntity<JsonNode> underscore =
        rest.exchange(
            "/store/customers?search=customer-it-likeesc-a_b",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(underscore.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(underscore.getBody().path("total_elements").asLong()).isZero();

    ResponseEntity<JsonNode> literal =
        rest.exchange(
            "/store/customers?search=customer-it-likeesc-acb",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(literal.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(literal.getBody().path("total_elements").asLong()).isGreaterThanOrEqualTo(1);
  }
}
