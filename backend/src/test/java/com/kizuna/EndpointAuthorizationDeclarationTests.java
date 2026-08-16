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
 * 全 HTTP handler が<b>方法級</b>の授権宣言を持ち、かつ method security の proxy が advise できる形（public・非 final・非
 * static）であることを機械検証する。{@code SecurityConfig} は {@code anyRequest().permitAll()} なので、宣言漏れも advise
 * 不能も等しく「誰でも叩ける端点」を静默に生む。クラス級の宣言を認めないのは、後から足した handler が公開設定を継承するため。
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
            "method security の proxy が advise できない handler（非 public / final / static）。授権注釈があっても実行時に無視され、端点は無防備になる")
        .isEmpty();
  }

  /** {@code RequestMappingHandlerMapping#createRequestMappingInfo} が映射を組む条件と同じ二種の注釈を見る。 */
  private static boolean isMapped(Method method) {
    return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
        || AnnotatedElementUtils.hasAnnotation(method, HttpExchange.class);
  }

  /** CGLIB は非 public・final・static を override できない。MVC は登録するが、授権の助言だけが黙って外れる。 */
  private static boolean isAdvisable(Method method) {
    int modifiers = method.getModifiers();
    return Modifier.isPublic(modifiers)
        && !Modifier.isFinal(modifiers)
        && !Modifier.isStatic(modifiers);
  }
}
