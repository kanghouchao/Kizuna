package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.security.PermitAll;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 全 handler の方法級授権と public・非 final・非 static を検証する（backend/AGENTS.md の API contract）。 SecurityConfig
 * が全要求を許可するため、宣言漏れ・proxy 対象外は公開端点となる。クラス級宣言も継承による公開を防ぐため認めない。 列挙は {@link ControllerHandlers}。
 */
class EndpointAuthorizationDeclarationTests {

  @Test
  @DisplayName("全 Controller の handler が方法級の授権宣言を持ち、proxy で advise 可能であること")
  void allHandlersDeclareAuthorization() throws Exception {
    List<ControllerHandlers.Handler> handlers = ControllerHandlers.all();

    List<String> undeclared = new ArrayList<>();
    List<String> unadvisable = new ArrayList<>();
    for (ControllerHandlers.Handler handler : handlers) {
      Method method = handler.method();
      if (!AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class)
          && !AnnotatedElementUtils.hasAnnotation(method, PermitAll.class)) {
        undeclared.add(handler.name());
      }
      if (!isAdvisable(method)) {
        unadvisable.add(handler.name());
      }
    }

    // 暗黙の no-op 防止: 走査が実際に handler を捉えていることを担保する。
    assertThat(handlers).as("com.kizuna 配下の handler メソッド").isNotEmpty();

    assertThat(undeclared)
        .as("方法級の @PreAuthorize も @PermitAll も宣言していない handler（授権無しの公開端点になる）")
        .isEmpty();
    assertThat(unadvisable)
        .as(
            "public でない・final・static な handler。private / final / static は proxy が advise できず授権注釈が実行時に黙って外れる。"
                + "protected / package-private は技術上は advise できるが、API 契約により handler は public に限る")
        .isEmpty();
  }

  /**
   * CGLIB が override できないのは private・final・static。protected / package-private は override
   * できるが、クラスの説明にある API 契約で handler は public に限るため、判定はあえて厳格側に置く。
   */
  private static boolean isAdvisable(Method method) {
    int modifiers = method.getModifiers();
    return Modifier.isPublic(modifiers)
        && !Modifier.isFinal(modifiers)
        && !Modifier.isStatic(modifiers);
  }
}
