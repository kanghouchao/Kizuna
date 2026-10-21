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
    // 店舗文脈を確立する StoreIdInterceptor（/store/**）やグループ級の作用域判定より先に解決器を差し込む。
    registry.addInterceptor(storeScopeInstallingInterceptor).order(Ordered.HIGHEST_PRECEDENCE);
  }
}
