package com.kizuna.store.infrastructure;

import com.kizuna.shared.storescope.StoreIdInterceptor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @NonNull private final StoreIdInterceptor storeIdInterceptor;
  @NonNull private final MaintenanceModeInterceptor maintenanceModeInterceptor;
  @NonNull private final StoreExistenceInterceptor storeExistenceInterceptor;

  public WebMvcConfig(
      @NonNull StoreIdInterceptor storeIdInterceptor,
      @NonNull MaintenanceModeInterceptor maintenanceModeInterceptor,
      @NonNull StoreExistenceInterceptor storeExistenceInterceptor) {
    this.storeIdInterceptor = storeIdInterceptor;
    this.maintenanceModeInterceptor = maintenanceModeInterceptor;
    this.storeExistenceInterceptor = storeExistenceInterceptor;
  }

  @Override
  public void addInterceptors(@NonNull InterceptorRegistry registry) {
    // メンテナンス判定は店舗コンテキスト設定より先に行う
    registry.addInterceptor(maintenanceModeInterceptor).addPathPatterns("/store/**");
    registry.addInterceptor(storeIdInterceptor).addPathPatterns("/store/**", "/files/**");
    // 店舗文脈確立（StoreIdInterceptor）の後段で、その store_id の実在性を検証する（#429・#398）
    registry.addInterceptor(storeExistenceInterceptor).addPathPatterns("/store/**", "/files/**");
  }
}
