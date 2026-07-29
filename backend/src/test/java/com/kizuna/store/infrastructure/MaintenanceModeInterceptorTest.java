package com.kizuna.store.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.ServiceUnavailableException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class MaintenanceModeInterceptorTest {

  @Mock private SystemConfigService systemConfigService;

  @InjectMocks private MaintenanceModeInterceptor interceptor;

  @Test
  @DisplayName("メンテナンスモード中はリクエストを 503 の例外で拒否すること")
  void preHandle_maintenanceOn() {
    when(systemConfigService.getConfigValue("maintenance_mode")).thenReturn(Optional.of("true"));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/store/orders");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessageContaining("メンテナンス中");
  }

  @Test
  @DisplayName("メンテナンスモードでなければ通過すること")
  void preHandle_maintenanceOff() throws Exception {
    when(systemConfigService.getConfigValue("maintenance_mode")).thenReturn(Optional.of("false"));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/store/orders");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isTrue();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("設定が存在しない場合は通過すること")
  void preHandle_configMissing() throws Exception {
    when(systemConfigService.getConfigValue("maintenance_mode")).thenReturn(Optional.empty());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/store/orders");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
  }
}
