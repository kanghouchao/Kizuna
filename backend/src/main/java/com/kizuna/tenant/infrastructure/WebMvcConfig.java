package com.kizuna.tenant.infrastructure;

import com.kizuna.shared.storescope.StoreIdInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @NonNull private final StoreIdInterceptor tenantIdInterceptor;
  @NonNull private final MaintenanceModeInterceptor maintenanceModeInterceptor;

  public WebMvcConfig(
      @NonNull StoreIdInterceptor tenantIdInterceptor,
      @NonNull MaintenanceModeInterceptor maintenanceModeInterceptor) {
    this.tenantIdInterceptor = tenantIdInterceptor;
    this.maintenanceModeInterceptor = maintenanceModeInterceptor;
  }

  @Override
  public void addInterceptors(@NonNull InterceptorRegistry registry) {
    // メンテナンス判定はテナントコンテキスト設定より先に行う
    registry.addInterceptor(maintenanceModeInterceptor).addPathPatterns("/store/**");
    registry.addInterceptor(tenantIdInterceptor).addPathPatterns("/store/**", "/files/**");
  }
}
