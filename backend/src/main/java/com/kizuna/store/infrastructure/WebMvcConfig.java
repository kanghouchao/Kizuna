package com.kizuna.store.infrastructure;

import com.kizuna.shared.storescope.StoreIdInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @NonNull private final StoreIdInterceptor storeIdInterceptor;
  @NonNull private final MaintenanceModeInterceptor maintenanceModeInterceptor;

  public WebMvcConfig(
      @NonNull StoreIdInterceptor storeIdInterceptor,
      @NonNull MaintenanceModeInterceptor maintenanceModeInterceptor) {
    this.storeIdInterceptor = storeIdInterceptor;
    this.maintenanceModeInterceptor = maintenanceModeInterceptor;
  }

  @Override
  public void addInterceptors(@NonNull InterceptorRegistry registry) {
    // メンテナンス判定は店舗コンテキスト設定より先に行う
    registry.addInterceptor(maintenanceModeInterceptor).addPathPatterns("/store/**");
    registry.addInterceptor(storeIdInterceptor).addPathPatterns("/store/**", "/files/**");
  }
}
