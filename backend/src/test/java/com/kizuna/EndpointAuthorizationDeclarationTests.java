package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.security.PermitAll;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 全 HTTP handler が<b>方法級</b>の授権宣言（{@code @PreAuthorize} か {@code @PermitAll}）を持つことを機械検証する。
 *
 * <p>{@code SecurityConfig} は {@code anyRequest().permitAll()} で、授権はこの二つの注釈だけが担う。クラス級の宣言は認めない —
 * 後から足した handler がクラスの公開設定を静默に継承してしまうため。走査は {@code @Controller} stereotype をメタ注釈ごと
 * 展開し（{@code @RestController} を含む）、handler の判定は {@code @RequestMapping} の合成注釈を辿る。
 */
class EndpointAuthorizationDeclarationTests {

  @Test
  @DisplayName("全 Controller の handler が方法級の @PreAuthorize か @PermitAll を宣言していること")
  void allHandlersDeclareAuthorization() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

    List<String> offenders = new ArrayList<>();
    List<String> scanned = new ArrayList<>();
    int handlers = 0;
    for (var candidate : scanner.findCandidateComponents("com.kizuna")) {
      Class<?> controller = Class.forName(candidate.getBeanClassName());
      scanned.add(controller.getSimpleName());
      // getMethods() は基底クラスから継承した公開 handler も含む（現状 Controller に継承は無いが、
      // 継承を導入した時にこのガードが静かに素通りしないようにする）。
      for (Method method : controller.getMethods()) {
        if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
          continue;
        }
        handlers++;
        if (!AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class)
            && !AnnotatedElementUtils.hasAnnotation(method, PermitAll.class)) {
          offenders.add(controller.getName() + "#" + method.getName());
        }
      }
    }

    // 暗黙の no-op 防止: 走査が実際に Controller と handler を捉えていることを担保する。
    assertThat(scanned).as("com.kizuna 配下の @Controller stereotype").isNotEmpty();
    assertThat(handlers).as("走査した handler メソッドの総数").isGreaterThan(0);

    assertThat(offenders)
        .as("方法級の @PreAuthorize も @PermitAll も宣言していない handler（授権無しの公開端点になる）")
        .isEmpty();
  }
}
