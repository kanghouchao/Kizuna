package com.kizuna.service.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.service.application.ServiceSettingsService;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** handler が X-Store-ID で名乗られた店舗をそのまま service へ渡すことの単体テスト。 */
@WebMvcTest(ServiceSettingsController.class)
@Import({ServiceSettingsControllerTest.MethodSecurityConfig.class, StoreScopeHolder.class})
class ServiceSettingsControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final Long STORE_ID = 7L;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ServiceSettingsService service;

  // CurrentStoreArgumentResolver（引数解決器）は @WebMvcTest に自動で取り込まれるため、
  // その依存もモックで満たす必要がある。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("一覧は名乗られた店舗の id で service を呼ぶこと")
  @WithMockUser(authorities = "PERM_SERVICE_MANAGE")
  void listPassesTheCurrentStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(get("/services").header("X-Store-ID", STORE_ID.toString()))
        .andExpect(status().isOk());

    verify(service)
        .list(
            STORE_ID,
            null,
            false,
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
  }

  @Test
  @DisplayName("削除は名乗られた店舗の id で service を呼ぶこと")
  @WithMockUser(authorities = "PERM_SERVICE_MANAGE")
  void deletePassesTheCurrentStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            delete("/services/s1")
                .param("expected_version", "2")
                .principal(() -> "operator@example.test")
                .header("X-Store-ID", STORE_ID.toString()))
        .andExpect(status().isNoContent());

    verify(service).delete(STORE_ID, "s1", 2L, "operator@example.test");
  }

  @Test
  @DisplayName("改訂履歴は改訂番号の新しい順の Page で返すこと")
  @WithMockUser(authorities = "PERM_SERVICE_MANAGE")
  void revisionsAreAPageNewestFirst() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    when(service.history(eq(STORE_ID), eq("s1"), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(get("/services/s1/revisions").header("X-Store-ID", STORE_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, "revisionNumber", "id"));
  }
}
