package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.security.PermitAll;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * 全 handler の方法級授権と public・非 final・非 static を検証する（backend/AGENTS.md の API contract）。 SecurityConfig
 * が全要求を許可するため、宣言漏れ・proxy 対象外は公開端点となる。クラス級宣言も継承による公開を防ぐため認めない。 列挙は Spring と同じ Controller
 * 判定・MethodIntrospector・RequestMapping／HttpExchange を使う。
 */
class EndpointAuthorizationDeclarationTests {

  @Test
  @DisplayName("全 Controller の handler が方法級の授権宣言を持ち、proxy で advise 可能であること")
  void allHandlersDeclareAuthorization() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

    List<String> undeclared = new ArrayList<>();
    List<String> unadvisable = new ArrayList<>();
    List<String> scanned = new ArrayList<>();
    int handlers = 0;
    for (var candidate : scanner.findCandidateComponents("com.kizuna")) {
      Class<?> type = Class.forName(candidate.getBeanClassName());
      // RequestMappingHandlerMapping#isHandler と同一の述語。型階層を辿るので、@Controller を持つ
      // 抽象基底クラスの具象サブクラスも handler として扱われる。
      if (!AnnotatedElementUtils.hasAnnotation(type, Controller.class)) {
        continue;
      }
      scanned.add(type.getSimpleName());
      Map<Method, Boolean> mapped =
          MethodIntrospector.selectMethods(
              type,
              (MethodIntrospector.MetadataLookup<Boolean>)
                  method -> isMapped(method) ? Boolean.TRUE : null);
      handlers += mapped.size();
      for (Method method : mapped.keySet()) {
        String name = type.getName() + "#" + method.getName();
        if (!AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class)
            && !AnnotatedElementUtils.hasAnnotation(method, PermitAll.class)) {
          undeclared.add(name);
        }
        if (!isAdvisable(method)) {
          unadvisable.add(name);
        }
      }
    }

    // 暗黙の no-op 防止: 走査が実際に Controller と handler を捉えていることを担保する。
    assertThat(scanned).as("com.kizuna 配下の Controller").isNotEmpty();
    assertThat(handlers).as("走査した handler メソッドの総数").isGreaterThan(0);

    assertThat(undeclared)
        .as("方法級の @PreAuthorize も @PermitAll も宣言していない handler（授権無しの公開端点になる）")
        .isEmpty();
    assertThat(unadvisable)
        .as(
            "public でない・final・static な handler。private / final / static は proxy が advise できず授権注釈が実行時に黙って外れる。"
                + "protected / package-private は技術上は advise できるが、API 契約により handler は public に限る")
        .isEmpty();
  }

  /** {@code RequestMappingHandlerMapping#createRequestMappingInfo} が映射を組む条件と同じ二種の注釈を見る。 */
  private static boolean isMapped(Method method) {
    return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
        || AnnotatedElementUtils.hasAnnotation(method, HttpExchange.class);
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
