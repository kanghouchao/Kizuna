package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * actuator の web 露出が承認済みリスト（health と prometheus）に固定され、gateway が転送しない管理ポートに置かれていることを機械検証する。管理端点は
 * controller ではないため {@code EndpointAuthorizationDeclarationTests} の走査に掛からず、露出を広げても授権ガードは緑のまま —
 * この断言だけが捉える。
 *
 * <p>検証対象は application.yml のリテラルであり、実行時の環境変数上書き（{@code MANAGEMENT_*}）までは捕捉できない。
 */
class ActuatorExposureDeclarationTests {
  private Object management;

  @BeforeEach
  void load() throws Exception {
    try (InputStream yml = getClass().getClassLoader().getResourceAsStream("application.yml")) {
      assertThat(yml).as("main の application.yml がテスト classpath に載っていること").isNotNull();
      Map<String, Object> root = new Yaml().load(yml);
      management = root.get("management");
      assertThat(management).as("management 節").isNotNull();
    }
  }

  @Test
  @DisplayName("management.endpoints.web.exposure.include が health と prometheus のみに固定されていること")
  void actuatorExposureIsPinnedToHealthAndPrometheus() {
    Object include = dig(management, "endpoints", "web", "exposure", "include");
    assertThat(include)
        .as("actuator の web 露出。広げるにはこの断言の更新＝レビューを通すこと")
        .isEqualTo(List.of("health", "prometheus"));
  }

  @Test
  @DisplayName("actuator が本体の 8080 ではなく管理ポート 8090 に置かれていること")
  void actuatorLivesOnTheManagementPort() {
    assertThat(dig(management, "server", "port"))
        .as("gateway は 8080 だけを転送する。管理ポートが無いと actuator が /api/actuator/... として公開される")
        .isEqualTo(8090);
  }

  private static Object dig(Object node, String... keys) {
    for (String key : keys) {
      assertThat(node).as("経路上の節 %s が Map であること", key).isInstanceOf(Map.class);
      node = ((Map<?, ?>) node).get(key);
    }
    return node;
  }
}
