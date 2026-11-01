package com.kizuna.shift.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.shift.application.ShiftLineageService;
import com.kizuna.shift.application.ShiftService;
import com.kizuna.user.application.StoreScopeResolver;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

  // CurrentStoreArgumentResolver（引数解決器）は @WebMvcTest に自動で取り込まれるため、
  // その依存もモックで満たす必要がある。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("一覧は名乗られた店舗の id で service を呼ぶこと")
  @WithMockUser(authorities = "PERM_SHIFT_MANAGE")
  void listPassesTheCurrentStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            get("/shifts")
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
        .perform(delete("/shifts/s1").header("X-Store-ID", STORE_ID.toString()))
        .andExpect(status().isNoContent());

    verify(shiftService).delete(STORE_ID, "s1");
  }

  @Test
  @DisplayName("匿名の公開出勤表もヘッダで名乗られた店舗の id で service を呼ぶこと")
  @WithAnonymousUser
  void publicListPassesTheNamedStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(get("/shifts/public").header("X-Store-ID", STORE_ID.toString()))
        .andExpect(status().isOk());

    verify(shiftService).listPublicToday(STORE_ID);
  }

  @Test
  @DisplayName("変更申請の履歴は提出の新しい順の Page で返すこと")
  @WithMockUser(authorities = "PERM_SHIFT_MANAGE")
  void changeRequestsAreAPageNewestFirst() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    when(shiftLineageService.changeRequests(eq(STORE_ID), eq("s1"), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(get("/shifts/s1/change-requests").header("X-Store-ID", STORE_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
  }
}
