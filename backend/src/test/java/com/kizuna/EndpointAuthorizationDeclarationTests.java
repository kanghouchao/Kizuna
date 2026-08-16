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
 * 全 HTTP handler が<b>方法級</b>の授権宣言を持ち、かつ <b>public・非 final・非 static</b> であることを機械検証する。{@code
 * SecurityConfig} は {@code anyRequest().permitAll()} なので、宣言漏れも advise
 * 不能も等しく「誰でも叩ける端点」を静默に生む。クラス級の宣言を認めないのは、後から足した handler が公開設定を継承するため。public 限定は規約（api-guidelines
 * §7）— CGLIB が advise できないのは private・final・static だけだが、例外形を作らないため public 以外を一律拒否する。
 *
 * <p>枚挙は Spring の {@code RequestMappingHandlerMapping} と同一規則（{@code isHandler} と同じ
 * {@code @Controller} 述語・{@code MethodIntrospector}・{@code @RequestMapping}／{@code @HttpExchange}）。
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
                + "protected / package-private は技術上は advise できるが、規約 §7 により handler は public に限る")
        .isEmpty();
  }

  /** {@code RequestMappingHandlerMapping#createRequestMappingInfo} が映射を組む条件と同じ二種の注釈を見る。 */
  private static boolean isMapped(Method method) {
    return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
        || AnnotatedElementUtils.hasAnnotation(method, HttpExchange.class);
  }

  /**
   * CGLIB が override できないのは private・final・static。protected / package-private は override
   * できるが、規約（§7）で handler は public に限るため、判定はあえて厳格側に置く。
   */
  private static boolean isAdvisable(Method method) {
    int modifiers = method.getModifiers();
    return Modifier.isPublic(modifiers)
        && !Modifier.isFinal(modifiers)
        && !Modifier.isStatic(modifiers);
  }
}
