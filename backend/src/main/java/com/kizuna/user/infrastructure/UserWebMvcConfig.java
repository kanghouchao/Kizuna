package com.kizuna.user.infrastructure;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class UserWebMvcConfig implements WebMvcConfigurer {

  @NonNull private final StoreScopeInstallingInterceptor storeScopeInstallingInterceptor;

  @Override
  public void addInterceptors(@NonNull InterceptorRegistry registry) {
    // 他の interceptor より先に解決器を差し込み、後続の interceptor・引数解決・handler が作用域を読めるようにする。
    registry.addInterceptor(storeScopeInstallingInterceptor).order(Ordered.HIGHEST_PRECEDENCE);
  }
}
