package com.kizuna.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 派生 findById（EntityManager.find 経路）に storeFilter が掛かることの実測。
 *
 * <p>複数店舗を授権された操作者は、店舗 A の文脈のまま店舗 B の実在 id を直接指せる——インターセプタは 授権集合しか見ないため、この越境を止められるのは
 * applyToLoadByKey を有効にした Hibernate フィルタだけ。 既存の *CrossStoreIT は単店授権者を使うためインターセプタの 403
 * で止まり、この経路を一度も踏んでいない。
 */
class StoreScopedLoadByKeyIT extends CrossStoreTestSupport {

  @Test
  @DisplayName("2店舗授権の店長が店舗Aの文脈で店舗Bの顧客 id を GET しても 404 で、実データが漏れないこと（正向対照つき）")
  void derivedFindByIdIsStoreFiltered() {
    String canary = "実測カナリア顧客_" + UUID.randomUUID();
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>("{\"name\": \"" + canary + "\"}", managerHeaders(STORE_B)),
            JsonNode.class);
    assertThat(created.getStatusCode()).as("前提: 店舗Bに顧客を作成できること").isEqualTo(HttpStatus.CREATED);
    String id = created.getBody().path("id").asString();

    ResponseEntity<String> sameStore =
        rest.exchange(
            "/store/customers/" + id,
            HttpMethod.GET,
            new HttpEntity<>(managerHeaders(STORE_B)),
            String.class);
    assertThat(sameStore.getStatusCode()).as("正向対照: 自店文脈では読めること").isEqualTo(HttpStatus.OK);

    ResponseEntity<String> crossStore =
        rest.exchange(
            "/store/customers/" + id,
            HttpMethod.GET,
            new HttpEntity<>(managerHeaders(STORE_A)),
            String.class);
    assertThat(crossStore.getStatusCode())
        .as("店舗Aの文脈から店舗Bの id の直指しは 404 になること")
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(crossStore.getBody()).as("生ボディに店舗Bの実データが現れないこと").doesNotContain(canary);
  }
}
