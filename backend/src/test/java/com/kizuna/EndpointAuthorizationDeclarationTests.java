package com.kizuna;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.security.PermitAll;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全 HTTP handler が授権宣言（{@code @PreAuthorize} か {@code @PermitAll}）を持つことを機械検証する。
 *
 * <p>{@code SecurityConfig} は {@code anyRequest().permitAll()} で、授権はこの二つの注釈だけが担う。宣言漏れは 403
 * ではなく「誰でも叩ける公開端点」を静默に生む。handler の判定も授権注釈の判定も合成注釈を辿るので、Spring の {@code
 * RequestMappingHandlerMapping} と同じ写像になり、{@code @GetMapping} 等や独自の合成注釈も取りこぼさない。
 */
class EndpointAuthorizationDeclarationTests {

  @Test
  @DisplayName("全 @RestController の handler が @PreAuthorize か @PermitAll を宣言していること")
  void allHandlersDeclareAuthorization() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

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
        if (!declaresAuthorization(method)
            && !declaresAuthorization(method.getDeclaringClass())
            && !declaresAuthorization(controller)) {
          offenders.add(controller.getName() + "#" + method.getName());
        }
      }
    }

    // 暗黙の no-op 防止: 走査が実際に Controller と handler を捉えていることを担保する。
    assertThat(scanned).as("com.kizuna 配下の @RestController").isNotEmpty();
    assertThat(handlers).as("走査した handler メソッドの総数").isGreaterThan(0);

    assertThat(offenders).as("@PreAuthorize も @PermitAll も宣言していない handler（授権無しの公開端点になる）").isEmpty();
  }

  private static boolean declaresAuthorization(AnnotatedElement element) {
    return AnnotatedElementUtils.hasAnnotation(element, PreAuthorize.class)
        || AnnotatedElementUtils.hasAnnotation(element, PermitAll.class);
  }
}
