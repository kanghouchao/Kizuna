package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 本番の application.yml のまま全体を起動し、actuator が gateway の転送する本体ポートから消え、管理ポートだけで Prometheus
 * 形式の指標を出すことを確かめる。管理ポートは application.yml が本体と別の値を持つときだけテスト側で乱数に置き換わるので、別ポートの設定が消えればこのテストが落ちる。
 */
@EnabledIfIntegration
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
// 全体の文脈（プールやスケジューラ）を後続の統合テストへ持ち越さない。DB 全体の待ちを数えるクラスがある。
@DirtiesContext
class ManagementPortIntegrationTest {
  private static final String TEMPLATED_ROUTE = "/orders/{id}/surcharge-revisions";

  // 起動時のバケット作成だけが S3 を要する。統合テストの環境に S3 は無い。
  @MockitoBean S3Client s3Client;

  @LocalServerPort int serverPort;
  @LocalManagementPort int managementPort;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    IntegrationTestDatabase.springDataSourceProperties(ManagementPortIntegrationTest.class)
        .forEach((key, value) -> registry.add(key, () -> value));
    registry.add("spring.data.redis.host", () -> System.getenv("KIZUNA_TEST_REDIS_HOST"));
    registry.add("app.jwt.secret", () -> UUID.randomUUID() + "-" + UUID.randomUUID());
  }

  @Test
  @DisplayName("本体ポートには actuator が無く、/readyz と /livez だけが応える")
  void mainPortServesProbesButNoActuator() throws Exception {
    assertThat(get(serverPort, "/actuator/health").statusCode())
        .as("本体ポートの /actuator/health")
        .isEqualTo(404);
    assertThat(get(serverPort, "/actuator/prometheus").statusCode())
        .as("本体ポートの /actuator/prometheus")
        .isEqualTo(404);
    assertThat(managementPort).as("管理ポートが本体と別であること").isNotEqualTo(serverPort);
    assertThat(get(serverPort, "/readyz").statusCode()).as("本体ポートの /readyz").isEqualTo(200);
    assertThat(get(serverPort, "/livez").statusCode()).as("本体ポートの /livez").isEqualTo(200);
  }

  @Test
  @DisplayName("管理ポートの /actuator/prometheus が経路の雛形と 1 秒の境界つきで要求の時間を出す")
  void managementPortExposesRequestHistogram() throws Exception {
    get(serverPort, "/orders/1/surcharge-revisions");

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              HttpResponse<String> scrape = get(managementPort, "/actuator/prometheus");
              assertThat(scrape.statusCode()).isEqualTo(200);
              assertThat(scrape.body().lines())
                  .as("雛形の uri と le=\"1.0\" を持つ bucket の行")
                  .anyMatch(
                      line ->
                          line.startsWith("http_server_requests_seconds_bucket{")
                              && line.contains("uri=\"" + TEMPLATED_ROUTE + "\"")
                              && line.contains("le=\"1.0\""));
            });
  }

  private HttpResponse<String> get(int port, String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(10))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
