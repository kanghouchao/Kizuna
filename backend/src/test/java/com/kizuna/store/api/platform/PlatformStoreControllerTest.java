package com.kizuna.store.api.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.store.api.dto.StoreVO;
import com.kizuna.store.application.PlatformStoreService;
import com.kizuna.store.application.StoreActivationService;
import com.kizuna.store.application.StoreRegistryService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 平台店舗 API のうち、ハンドラ層で閉じている約束（ページングの全順序・要求本文の検査）の単体テスト。 */
@WebMvcTest(PlatformStoreController.class)
@Import({PlatformStoreControllerTest.MethodSecurityConfig.class, StoreContext.class})
class PlatformStoreControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PlatformStoreService platformStoreService;
  @MockitoBean private StoreRegistryService storeRegistryService;

  // MaintenanceModeInterceptor / StoreExistenceInterceptor / StoreActivationInterceptor は
  // HandlerInterceptor として @WebMvcTest に自動で取り込まれるため、その依存もモックで満たす必要がある。
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreActivationService storeActivationService;

  @Test
  @DisplayName("GET /platform/stores?sort=name でも id が副キーとして補われること")
  @WithMockUser(authorities = "PERM_STORE_MANAGE")
  void listAppendsIdTiebreakerWhenCallerOverridesSort() throws Exception {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    Page<StoreVO> empty = new PageImpl<>(List.of());
    when(storeRegistryService.list(any(), pageableCaptor.capture())).thenReturn(empty);

    mockMvc.perform(get("/platform/stores?sort=name")).andExpect(status().isOk());

    assertThat(pageableCaptor.getValue().getSort().getOrderFor("id")).isNotNull();
  }

  // 更新は全項目の置換。必須項目を欠く要求を通すと既存値が null で潰れるため、
  // ハンドラ引数の検査（@Valid）が効いていることを固定する。
  @Test
  @DisplayName("PUT /platform/stores/{id} は email を欠く要求を検査で弾き、サービスへ渡さないこと")
  @WithMockUser(authorities = "PERM_STORE_MANAGE")
  void updateRejectsRequestMissingEmailBeforeService() throws Exception {
    mockMvc
        .perform(
            put("/platform/stores/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"店舗\"}"))
        .andExpect(status().isBadRequest());

    verify(storeRegistryService, never()).update(anyString(), any());
  }
}
