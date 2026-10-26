package com.kizuna.shift.api.store;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.shift.application.ShiftLineageService;
import com.kizuna.shift.application.ShiftService;
import com.kizuna.user.application.StoreScopeResolver;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** handler が X-Store-ID で名乗られた店舗をそのまま service へ渡すことの単体テスト。 */
@WebMvcTest(ShiftController.class)
@Import({ShiftControllerTest.MethodSecurityConfig.class, StoreScopeHolder.class})
class ShiftControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final Long STORE_ID = 7L;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ShiftService shiftService;
  @MockitoBean private ShiftLineageService shiftLineageService;

  // MaintenanceModeInterceptor（HandlerInterceptor）と CurrentStoreArgumentResolver（引数解決器）は
  // @WebMvcTest に自動で取り込まれるため、その依存もモックで満たす必要がある。
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("一覧は名乗られた店舗の id で service を呼ぶこと")
  @WithMockUser(authorities = "PERM_SHIFT_MANAGE")
  void listPassesTheCurrentStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            get("/store/shifts")
                .param("from", "2026-07-01")
                .param("to", "2026-07-31")
                .header("X-Store-ID", STORE_ID.toString()))
        .andExpect(status().isOk());

    verify(shiftService).list(STORE_ID, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
  }

  @Test
  @DisplayName("削除は名乗られた店舗の id で service を呼ぶこと")
  @WithMockUser(authorities = "PERM_SHIFT_MANAGE")
  void deletePassesTheCurrentStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(delete("/store/shifts/s1").with(csrf()).header("X-Store-ID", STORE_ID.toString()))
        .andExpect(status().isNoContent());

    verify(shiftService).delete(STORE_ID, "s1");
  }

  @Test
  @DisplayName("匿名の公開出勤表もヘッダで名乗られた店舗の id で service を呼ぶこと")
  @WithAnonymousUser
  void publicListPassesTheNamedStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(get("/store/shifts/public").header("X-Store-ID", STORE_ID.toString()))
        .andExpect(status().isOk());

    verify(shiftService).listPublicToday(STORE_ID);
  }
}
