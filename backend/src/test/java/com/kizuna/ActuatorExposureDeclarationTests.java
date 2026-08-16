package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * actuator の web 露出が承認済みリスト（health のみ）に固定されていることを機械検証する。管理端点は controller ではないため {@code
 * EndpointAuthorizationDeclarationTests} の走査に掛からず、露出を広げても授権ガードは緑のまま — この断言だけが捉える。
 *
 * <p>検証対象は application.yml のリテラルであり、実行時の環境変数上書き（{@code MANAGEMENT_*}）までは捕捉できない。
 */
class ActuatorExposureDeclarationTests {

  @Test
  @DisplayName("management.endpoints.web.exposure.include が health のみに固定されていること")
  void actuatorExposureIsPinnedToHealth() throws Exception {
    try (InputStream yml = getClass().getClassLoader().getResourceAsStream("application.yml")) {
      assertThat(yml).as("main の application.yml がテスト classpath に載っていること").isNotNull();
      Map<String, Object> root = new Yaml().load(yml);

      Object management = root.get("management");
      assertThat(management).as("management 節").isNotNull();
      Object include = dig(management, "endpoints", "web", "exposure", "include");
      assertThat(include).as("actuator の web 露出。広げるにはこの断言の更新＝レビューを通すこと").isEqualTo("health");
    }
  }

  private static Object dig(Object node, String... keys) {
    for (String key : keys) {
      assertThat(node).as("経路上の節 %s が Map であること", key).isInstanceOf(Map.class);
      node = ((Map<?, ?>) node).get(key);
    }
    return node;
  }
}
