package com.kizuna.store.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.storescope.CurrentStore;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

@ExtendWith(MockitoExtension.class)
class MaintenanceModeInterceptorTest {

  @Mock private SystemConfigService systemConfigService;

  @InjectMocks private MaintenanceModeInterceptor interceptor;

  /** 現在店舗の宣言の有無と required を切り替えて HandlerMethod を組み立てるための土台。 */
  static class Handlers {
    public void requiredStore(String other, @CurrentStore Long storeId) {}

    public void optionalStore(@CurrentStore(required = false) Long storeId) {}

    public void noStore(Long storeId) {}
  }

  private static HandlerMethod handler(String name, Class<?>... parameterTypes)
      throws NoSuchMethodException {
    return new HandlerMethod(new Handlers(), Handlers.class.getMethod(name, parameterTypes));
  }

  private boolean preHandle(Object handler) throws Exception {
    return interceptor.preHandle(
        new MockHttpServletRequest("GET", "/orders"), new MockHttpServletResponse(), handler);
  }

  private void maintenance(Optional<String> value) {
    when(systemConfigService.getConfigValue("maintenance_mode")).thenReturn(value);
  }

  @Test
  @DisplayName("メンテナンス中は現在店舗を必須とする handler を 503 の例外で拒否すること")
  void requiredStoreIsUnavailableDuringMaintenance() {
    maintenance(Optional.of("true"));

    assertThatThrownBy(() -> preHandle(handler("requiredStore", String.class, Long.class)))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessageContaining("メンテナンス中");
  }

  @Test
  @DisplayName("メンテナンス中でも現在店舗が任意の handler は通過すること")
  void optionalStorePassesDuringMaintenance() throws Exception {
    // 正しい実装は設定を読まないため、読んだ場合にだけ 503 へ倒れるよう寛容な stub で仕掛ける。
    lenient()
        .when(systemConfigService.getConfigValue("maintenance_mode"))
        .thenReturn(Optional.of("true"));

    assertThat(preHandle(handler("optionalStore", Long.class))).isTrue();
  }

  @Test
  @DisplayName("現在店舗を宣言しない handler は設定を読まずに通過すること")
  void handlerWithoutCurrentStoreSkipsTheConfig() throws Exception {
    assertThat(preHandle(handler("noStore", Long.class))).isTrue();
    verifyNoInteractions(systemConfigService);
  }

  @Test
  @DisplayName("HandlerMethod 以外の handler は設定を読まずに通過すること")
  void nonHandlerMethodSkipsTheConfig() throws Exception {
    assertThat(preHandle(new Object())).isTrue();
    verifyNoInteractions(systemConfigService);
  }

  @Test
  @DisplayName("メンテナンスモードでなければ通過すること")
  void maintenanceOffPasses() throws Exception {
    maintenance(Optional.of("false"));

    assertThat(preHandle(handler("requiredStore", String.class, Long.class))).isTrue();
  }

  @Test
  @DisplayName("設定が存在しない場合は通過すること")
  void missingConfigPasses() throws Exception {
    maintenance(Optional.empty());

    assertThat(preHandle(handler("requiredStore", String.class, Long.class))).isTrue();
  }
}
