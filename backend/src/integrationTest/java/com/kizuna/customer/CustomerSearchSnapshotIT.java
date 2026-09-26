package com.kizuna.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerListRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;

class CustomerSearchSnapshotIT extends CrossStoreTestSupport {
  @MockitoSpyBean CustomerListRepository customers;

  @Test
  void searchEvidenceKeepsTheSnapshotWhenAContactIsDeletedConcurrently() throws Exception {
    var headers = managerHeaders(STORE_A);
    var created =
        rest.postForEntity(
            "/store/customers", new HttpEntity<>(Map.of("name", "検索断面"), headers), JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = created.getBody().path("id").asString();
    String value = "Snapshot" + System.nanoTime();
    String path = "/store/customers/" + id + "/contacts";
    var contact =
        rest.postForEntity(
            path,
            new HttpEntity<>(Map.of("type", "LINE", "value", value), headers),
            JsonNode.class);
    assertThat(contact.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String contactId = contact.getBody().path("id").asString();
    var selected = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    // 実際のページ取得直後だけを止め、結果と一致理由の間に別の HTTP 更新をコミットする。
    doAnswer(
            invocation -> {
              Object result =
                  mockingDetails(customers)
                      .getMockCreationSettings()
                      .getDefaultAnswer()
                      .answer(invocation);
              selected.countDown();
              assertThat(resume.await(20, TimeUnit.SECONDS)).isTrue();
              return result;
            })
        .when(customers)
        .findAll(ArgumentMatchers.<Specification<Customer>>any(), any(Pageable.class));
    var search =
        CompletableFuture.supplyAsync(
            () ->
                rest.exchange(
                    "/store/customers?search=" + value,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    JsonNode.class));
    try {
      assertThat(selected.await(20, TimeUnit.SECONDS)).isTrue();
      var deleted =
          rest.exchange(
              path + "/" + contactId, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
      assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    } finally {
      resume.countDown();
    }
    var response = search.get(20, TimeUnit.SECONDS);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var rows = response.getBody().path("content");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).path("id").asString()).isEqualTo(id);
    var evidence = rows.get(0).path("matched_contacts");
    assertThat(evidence).hasSize(1);
    assertThat(evidence.get(0).path("id").asString()).isEqualTo(contactId);
    assertThat(evidence.get(0).path("value").asString()).isEqualTo(value);
    var after =
        rest.exchange(
            "/store/customers?search=" + value,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class);
    assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(after.getBody().path("content")).isEmpty();
  }
}
