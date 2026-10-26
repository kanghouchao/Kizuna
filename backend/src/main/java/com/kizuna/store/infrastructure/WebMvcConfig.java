package com.kizuna.store.infrastructure;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

  @NonNull private final MaintenanceModeInterceptor maintenanceModeInterceptor;

  @Override
  public void addInterceptors(@NonNull InterceptorRegistry registry) {
    // 対象は handler の宣言（必須の @CurrentStore）で決まるため、パスでは絞らない。
    registry.addInterceptor(maintenanceModeInterceptor);
  }
}
