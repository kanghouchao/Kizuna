package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.security.PermitAll;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * 全 HTTP handler が<b>方法級</b>の授権宣言（{@code @PreAuthorize} か {@code @PermitAll}）を持つことを機械検証する。
 *
 * <p>{@code SecurityConfig} は {@code anyRequest().permitAll()} で、授権はこの二つの注釈だけが担う。クラス級の宣言は認めない —
 * 後から足した handler がクラスの公開設定を静默に継承してしまうため。
 *
 * <p>端点の枚挙規則は Spring の {@code RequestMappingHandlerMapping} と同一に揃える（{@code @Controller} stereotype・
 * {@code MethodIntrospector} による非 public 込みの列挙・{@code @RequestMapping}／{@code @HttpExchange}
 * の合成注釈）。 近似で書くと、その差分がそのまま無授権端点の抜け道になる。
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
      Map<Method, Boolean> mapped =
          MethodIntrospector.selectMethods(
              controller,
              (MethodIntrospector.MetadataLookup<Boolean>)
                  method -> isMapped(method) ? Boolean.TRUE : null);
      handlers += mapped.size();
      for (Method method : mapped.keySet()) {
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

  /** {@code RequestMappingHandlerMapping#createRequestMappingInfo} が映射を組む条件と同じ二種の注釈を見る。 */
  private static boolean isMapped(Method method) {
    return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
        || AnnotatedElementUtils.hasAnnotation(method, HttpExchange.class);
  }
}
