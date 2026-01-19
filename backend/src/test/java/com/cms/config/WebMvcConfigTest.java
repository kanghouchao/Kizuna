package com.cms.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.config.interceptor.TenantIdInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

@SuppressWarnings("null")
class WebMvcConfigTest {

  @Test
  void addsTenantInterceptorToRegistry() {
    TenantIdInterceptor interceptor = mock(TenantIdInterceptor.class);
    InterceptorRegistry registry = mock(InterceptorRegistry.class);
    org.springframework.web.servlet.config.annotation.InterceptorRegistration registration =
        mock(org.springframework.web.servlet.config.annotation.InterceptorRegistration.class);

    // When addInterceptor is called, return the mock registration to prevent NPE on chained calls
    when(registry.addInterceptor(interceptor)).thenReturn(registration);

    WebMvcConfig config = new WebMvcConfig(interceptor);
    config.addInterceptors(registry);

    verify(registry).addInterceptor(interceptor);
    verify(registration).addPathPatterns("/tenant/**");
  }
}
