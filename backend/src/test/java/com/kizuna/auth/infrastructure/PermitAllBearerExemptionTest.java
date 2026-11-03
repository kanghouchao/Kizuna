package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.ControllerHandlers;
import com.kizuna.auth.infrastructure.ExemptingBearerTokenResolver.Exemption;
import jakarta.annotation.security.PermitAll;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Bearer の免除表は {@code @PermitAll} の handler を手で写したものなので、両者を双方向に突き合わせる。公開端点を足して免除表への登録を
 * 忘れると、陳腐なアクセストークンを持つタブだけがその端点で 401 になり、通常の試験では表に出ない。
 */
class PermitAllBearerExemptionTest {

  /** 意図して免除しない {@code @PermitAll} の端点と、その理由。 */
  private static final Map<Endpoint, String> NOT_EXEMPTED =
      Map.of(
          new Endpoint(HttpMethod.GET, "/auth/initial-setup"),
          "初期設定画面のサーバー側から Bearer なしで呼ばれ、ブラウザのアクセストークンが付かない");

  private static List<Endpoint> permitAll;

  record Endpoint(HttpMethod method, String path) {
    boolean coveredBy(Exemption exemption) {
      return exemption.pattern().equals(path)
          && (exemption.method() == null || exemption.method().equals(method));
    }

    @Override
    public String toString() {
      return method + " " + path;
    }
  }

  @BeforeAll
  static void scanPermitAllHandlers() throws Exception {
    permitAll = new ArrayList<>();
    for (ControllerHandlers.Handler handler : ControllerHandlers.all()) {
      if (AnnotatedElementUtils.hasAnnotation(handler.method(), PermitAll.class)) {
        permitAll.add(endpointOf(handler));
      }
    }
    // 暗黙の no-op 防止: 走査が実際に公開端点を捉えていることを担保する。
    assertThat(permitAll).as("@PermitAll の handler").isNotEmpty();
  }

  @Test
  @DisplayName("@PermitAll の handler はすべて Bearer の免除表に載っているか、理由付きの例外であること")
  void everyPermitAllHandlerIsExempted() {
    List<Endpoint> missing =
        permitAll.stream()
            .filter(endpoint -> !NOT_EXEMPTED.containsKey(endpoint))
            .filter(
                endpoint ->
                    ExemptingBearerTokenResolver.EXEMPTIONS.stream().noneMatch(endpoint::coveredBy))
            .toList();

    assertThat(missing).as("免除表にも例外にも無い @PermitAll の handler").isEmpty();
  }

  @Test
  @DisplayName("免除表の各行は、いずれかの @PermitAll の handler に対応すること")
  void everyExemptionNamesAPermitAllHandler() {
    List<Exemption> orphaned =
        ExemptingBearerTokenResolver.EXEMPTIONS.stream()
            .filter(exemption -> permitAll.stream().noneMatch(e -> e.coveredBy(exemption)))
            .toList();

    assertThat(orphaned).as("@PermitAll の handler に対応しない免除").isEmpty();
  }

  @Test
  @DisplayName("例外の各行は、免除表に載っていない @PermitAll の handler であること")
  void everyExceptionNamesAnUnexemptedPermitAllHandler() {
    assertThat(permitAll).containsAll(NOT_EXEMPTED.keySet());
    assertThat(NOT_EXEMPTED.keySet())
        .allSatisfy(
            endpoint ->
                assertThat(ExemptingBearerTokenResolver.EXEMPTIONS).noneMatch(endpoint::coveredBy));
  }

  /** クラスと handler の {@link RequestMapping} を連結する。複数のパス・メソッドを持つ handler は表と一対一に対応しないので拒む。 */
  private static Endpoint endpointOf(ControllerHandlers.Handler handler) {
    RequestMapping onType =
        AnnotatedElementUtils.findMergedAnnotation(handler.type(), RequestMapping.class);
    RequestMapping onMethod =
        AnnotatedElementUtils.findMergedAnnotation(handler.method(), RequestMapping.class);
    assertThat(onMethod).as(handler.name()).isNotNull();
    assertThat(onMethod.method()).as(handler.name() + " のメソッド").hasSize(1);
    return new Endpoint(
        HttpMethod.valueOf(onMethod.method()[0].name()),
        singlePath(onType, handler) + singlePath(onMethod, handler));
  }

  private static String singlePath(RequestMapping mapping, ControllerHandlers.Handler handler) {
    if (mapping == null || mapping.path().length == 0) {
      return "";
    }
    assertThat(mapping.path()).as(handler.name() + " のパス").hasSize(1);
    return mapping.path()[0];
  }
}
