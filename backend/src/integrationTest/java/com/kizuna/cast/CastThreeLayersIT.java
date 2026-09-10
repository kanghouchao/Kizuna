package com.kizuna.cast;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.api.dto.CastPublicResponse;
import com.kizuna.shared.CrossStoreTestSupport;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

class CastThreeLayersIT extends CrossStoreTestSupport {
  private JsonNode create() {
    var response =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>(Map.of("name", "三層検証"), storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody();
  }

  private JsonNode publicCasts() {
    var headers = storeHeaders(STORE_A);
    headers.setBearerAuth("broken-bearer");
    var response =
        rest.exchange(
            "/store/casts/public", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private boolean visible(String id) {
    for (JsonNode row : publicCasts()) if (row.path("id").asString().equals(id)) return true;
    return false;
  }

  private void publish(String id, String state) {
    var response =
        rest.exchange(
            "/store/casts/" + id + "/publication",
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("publication_status", state), storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().path("publication_status").asString()).isEqualTo(state);
  }

  @Test
  void publicExposureRequiresPublicationAndEnrollment() {
    var created = create();
    String id = created.path("id").asString();
    assertThat(created.path("publication_status").asString()).isEqualTo("UNPUBLISHED");
    assertThat(visible(id)).isFalse();
    publish(id, "PUBLISHED");
    assertThat(visible(id)).isTrue();
    var suspended =
        rest.exchange(
            "/store/casts/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("status", "SUSPENDED"), storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(suspended.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(visible(id)).isFalse();
    rest.exchange(
        "/store/casts/" + id,
        HttpMethod.PUT,
        new HttpEntity<>(Map.of("status", "ENROLLED"), storeHeaders(STORE_A)),
        JsonNode.class);
    assertThat(visible(id)).isTrue();
    publish(id, "UNPUBLISHED");
    assertThat(visible(id)).isFalse();
    assertThat(Arrays.stream(CastPublicResponse.class.getDeclaredFields()).map(Field::getName))
        .doesNotContain(
            "status",
            "publicationStatus",
            "createdAt",
            "updatedAt",
            "castId",
            "platformUserId",
            "realName",
            "birthDate");
  }

  @Test
  void internalFieldCannotBeRepublishedByChangingOrRecreatingDefinition() {
    String key = "secret_" + System.nanoTime();
    var definition =
        rest.postForEntity(
            "/store/casts/fields",
            new HttpEntity<>(
                Map.of("key", key, "label", "内部メモ", "is_public", false), managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(definition.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String definitionId = definition.getBody().path("id").asString();
    String id = create().path("id").asString();
    String secret = "内部契約-" + System.nanoTime();
    var updated =
        rest.exchange(
            "/store/casts/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("custom_fields", Map.of(key, secret)), storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updated.getBody().path("custom_fields").path(key).asString()).isEqualTo(secret);
    publish(id, "PUBLISHED");
    assertThat(publicCasts().toString()).doesNotContain(secret);
    var flip =
        rest.exchange(
            "/store/casts/fields/" + definitionId,
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("is_public", true), managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(flip.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    var removed =
        rest.exchange(
            "/store/casts/fields/" + definitionId,
            HttpMethod.DELETE,
            new HttpEntity<>(managerHeaders(STORE_A)),
            Void.class);
    assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var recreated =
        rest.postForEntity(
            "/store/casts/fields",
            new HttpEntity<>(
                Map.of("key", key, "label", "公開メモ", "is_public", true), managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(recreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(publicCasts().toString()).doesNotContain(secret);
    var detail =
        rest.exchange(
            "/store/casts/" + id,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(detail.getBody().path("custom_fields").has(key)).isFalse();
    rest.exchange(
        "/store/casts/fields/" + recreated.getBody().path("id").asString(),
        HttpMethod.DELETE,
        new HttpEntity<>(managerHeaders(STORE_A)),
        Void.class);
  }
}
