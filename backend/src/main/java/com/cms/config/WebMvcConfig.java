package com.cms.config;

import com.cms.config.interceptor.TenantIdInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @NonNull private final TenantIdInterceptor tenantIdInterceptor;

  public WebMvcConfig(@NonNull TenantIdInterceptor tenantIdInterceptor) {
    this.tenantIdInterceptor = tenantIdInterceptor;
  }

  @Override
  public void addInterceptors(@NonNull InterceptorRegistry registry) {
    registry.addInterceptor(tenantIdInterceptor).addPathPatterns("/tenant/**");
  }
}
