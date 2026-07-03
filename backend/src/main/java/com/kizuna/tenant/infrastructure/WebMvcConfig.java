package com.kizuna.tenant.infrastructure;

import com.kizuna.shared.tenancy.TenantIdInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @NonNull private final TenantIdInterceptor tenantIdInterceptor;
  @NonNull private final MaintenanceModeInterceptor maintenanceModeInterceptor;

  public WebMvcConfig(
      @NonNull TenantIdInterceptor tenantIdInterceptor,
      @NonNull MaintenanceModeInterceptor maintenanceModeInterceptor) {
    this.tenantIdInterceptor = tenantIdInterceptor;
    this.maintenanceModeInterceptor = maintenanceModeInterceptor;
  }

  @Override
  public void addInterceptors(@NonNull InterceptorRegistry registry) {
    // メンテナンス判定はテナントコンテキスト設定より先に行う
    registry.addInterceptor(maintenanceModeInterceptor).addPathPatterns("/tenant/**");
    registry.addInterceptor(tenantIdInterceptor).addPathPatterns("/tenant/**", "/files/**");
  }
}
