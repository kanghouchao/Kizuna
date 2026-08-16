package com.kizuna.settings.api.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.settings.api.dto.SystemConfigResponse;
import com.kizuna.settings.api.dto.SystemConfigUpdateRequest;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.store.application.StoreActivationService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlatformConfigController.class)
@Import({PlatformConfigControllerTest.MethodSecurityConfig.class, StoreContext.class})
class PlatformConfigControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SystemConfigService systemConfigService;

  // StoreExistenceInterceptor（HandlerInterceptor）の依存。@WebMvcTest は HandlerInterceptor を
  // 取り込むため、MaintenanceModeInterceptor の SystemConfigService と同様にポートのモックを用意する。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreActivationService storeActivationService;

  @Test
  @DisplayName("PERM_SYSTEM_CONFIG_MANAGE 権限があれば設定一覧を取得できること")
  @WithMockUser(authorities = "PERM_SYSTEM_CONFIG_MANAGE")
  void listWithAuthority() throws Exception {
    // 準備
    SystemConfigResponse response =
        SystemConfigResponse.builder().configKey("site_name").configValue("Kizuna").build();
    when(systemConfigService.getAllConfigs()).thenReturn(List.of(response));

    // 実行・検証
    mockMvc
        .perform(get("/platform/configs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].config_key").value("site_name"));
  }

  @Test
  @DisplayName("PERM_SYSTEM_CONFIG_MANAGE 権限がなければ 403 を返すこと")
  @WithMockUser(authorities = "OTHER_PERMISSION")
  void listWithoutAuthority() throws Exception {
    // 実行・検証
    mockMvc.perform(get("/platform/configs")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("PERM_SYSTEM_CONFIG_MANAGE 権限がなければ更新も 403 を返すこと")
  @WithMockUser(authorities = "OTHER_PERMISSION")
  void updateWithoutAuthority() throws Exception {
    // 実行・検証
    mockMvc
        .perform(
            put("/platform/configs/site_name")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"config_value\":\"新しい名前\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("存在しない設定キーの更新は 400 とエラーメッセージを返すこと")
  @WithMockUser(authorities = "PERM_SYSTEM_CONFIG_MANAGE")
  void updateUnknownKey() throws Exception {
    // 準備
    when(systemConfigService.updateConfig(any(String.class), any(SystemConfigUpdateRequest.class)))
        .thenThrow(new ServiceException("設定キーが見つかりません: unknown_key"));

    // 実行・検証
    mockMvc
        .perform(
            put("/platform/configs/unknown_key")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"config_value\":\"v\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("設定キーが見つかりません: unknown_key"));
  }

  /** 実在する設定キー（{@code seed} が入れる形）が path 変数として欠けずにサービスへ届くことを固定する。 */
  @Test
  @DisplayName("更新の宛先は path の設定キーで、値だけを本体で受けること")
  @WithMockUser(authorities = "PERM_SYSTEM_CONFIG_MANAGE")
  void updateAddressesTheKeyInThePath() throws Exception {
    when(systemConfigService.updateConfig(any(String.class), any(SystemConfigUpdateRequest.class)))
        .thenReturn(
            SystemConfigResponse.builder()
                .configKey("line_channel_id")
                .configValue("1234567890")
                .build());

    mockMvc
        .perform(
            put("/platform/configs/line_channel_id")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"config_value\":\"1234567890\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.config_key").value("line_channel_id"));

    ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<SystemConfigUpdateRequest> request =
        ArgumentCaptor.forClass(SystemConfigUpdateRequest.class);
    verify(systemConfigService).updateConfig(key.capture(), request.capture());
    assertThat(key.getValue()).isEqualTo("line_channel_id");
    assertThat(request.getValue().getConfigValue()).isEqualTo("1234567890");
  }
}
