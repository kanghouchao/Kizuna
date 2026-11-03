package com.kizuna;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * 主コードの全 handler を Spring と同じ Controller 判定・MethodIntrospector・RequestMapping／HttpExchange で列挙する。
 */
public final class ControllerHandlers {

  public record Handler(Class<?> type, Method method) {
    public String name() {
      return type.getName() + "#" + method.getName();
    }
  }

  private ControllerHandlers() {}

  public static List<Handler> all() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

    List<Handler> handlers = new ArrayList<>();
    for (var candidate : scanner.findCandidateComponents("com.kizuna")) {
      Class<?> type = Class.forName(candidate.getBeanClassName());
      // RequestMappingHandlerMapping#isHandler と同一の述語。型階層を辿るので、@Controller を持つ
      // 抽象基底クラスの具象サブクラスも handler として扱われる。
      if (!AnnotatedElementUtils.hasAnnotation(type, Controller.class)) {
        continue;
      }
      MethodIntrospector.selectMethods(
              type,
              (MethodIntrospector.MetadataLookup<Boolean>)
                  method -> isMapped(method) ? Boolean.TRUE : null)
          .keySet()
          .forEach(method -> handlers.add(new Handler(type, method)));
    }
    return handlers;
  }

  /** {@code RequestMappingHandlerMapping#createRequestMappingInfo} が映射を組む条件と同じ二種の注釈を見る。 */
  private static boolean isMapped(Method method) {
    return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
        || AnnotatedElementUtils.hasAnnotation(method, HttpExchange.class);
  }
}
