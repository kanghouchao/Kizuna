package com.kizuna.settings.api.central;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.auth.infrastructure.JwtUtil;
import com.kizuna.auth.infrastructure.TokenBlacklistService;
import com.kizuna.settings.api.dto.SystemConfigResponse;
import com.kizuna.settings.api.dto.SystemConfigUpdateRequest;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.JacksonConfig;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CentralConfigController.class)
@Import({
  CentralConfigControllerTest.MethodSecurityConfig.class,
  JacksonConfig.class,
  TenantContext.class
})
class CentralConfigControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SystemConfigService systemConfigService;

  // JwtAuthenticationFilter の依存（Authorization ヘッダーなしでは素通しのため動作に影響しない）
  @MockitoBean private JwtUtil jwtUtil;

  @MockitoBean private TokenBlacklistService tokenBlacklistService;

  @Test
  @DisplayName("SYSTEM_CONFIG 権限があれば設定一覧を取得できること")
  @WithMockUser(authorities = "SYSTEM_CONFIG")
  void listWithAuthority() throws Exception {
    // 準備
    SystemConfigResponse response =
        SystemConfigResponse.builder().configKey("site_name").configValue("Kizuna").build();
    when(systemConfigService.getAllConfigs()).thenReturn(List.of(response));

    // 実行・検証
    mockMvc
        .perform(get("/central/configs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].config_key").value("site_name"));
  }

  @Test
  @DisplayName("SYSTEM_CONFIG 権限がなければ 403 を返すこと")
  @WithMockUser(authorities = "OTHER_PERMISSION")
  void listWithoutAuthority() throws Exception {
    // 実行・検証
    mockMvc.perform(get("/central/configs")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("SYSTEM_CONFIG 権限がなければ更新も 403 を返すこと")
  @WithMockUser(authorities = "OTHER_PERMISSION")
  void updateWithoutAuthority() throws Exception {
    // 実行・検証
    mockMvc
        .perform(
            put("/central/configs")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"config_key\":\"site_name\",\"config_value\":\"新しい名前\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("存在しない設定キーの更新は 400 とエラーメッセージを返すこと")
  @WithMockUser(authorities = "SYSTEM_CONFIG")
  void updateUnknownKey() throws Exception {
    // 準備
    when(systemConfigService.updateConfig(any(SystemConfigUpdateRequest.class)))
        .thenThrow(new ServiceException("設定キーが見つかりません: unknown_key"));

    // 実行・検証
    mockMvc
        .perform(
            put("/central/configs")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"config_key\":\"unknown_key\",\"config_value\":\"v\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("設定キーが見つかりません: unknown_key"));
  }
}
