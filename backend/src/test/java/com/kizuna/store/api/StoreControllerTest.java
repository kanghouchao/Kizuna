package com.kizuna.store.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.store.api.dto.StoreVO;
import com.kizuna.store.application.AdminStoreService;
import com.kizuna.store.application.StoreRegistryService;
import com.kizuna.user.application.StoreScopeResolver;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/** グループ級店舗 API のうち、ハンドラ層で閉じている約束（ページングの全順序・要求本文の検査）の単体テスト。 */
@WebMvcTest(StoreController.class)
@Import({StoreControllerTest.MethodSecurityConfig.class, StoreScopeHolder.class})
class StoreControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AdminStoreService adminStoreService;
  @MockitoBean private StoreRegistryService storeRegistryService;

  // CurrentStoreArgumentResolver（引数解決器）は @WebMvcTest に自動で取り込まれるため、
  // その依存もモックで満たす必要がある。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("GET /stores?sort=name でも id が副キーとして補われること")
  @WithMockUser(authorities = "PERM_STORE_MANAGE")
  void listAppendsIdTiebreakerWhenCallerOverridesSort() throws Exception {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    Page<StoreVO> empty = new PageImpl<>(List.of());
    when(storeRegistryService.list(any(), pageableCaptor.capture())).thenReturn(empty);

    mockMvc.perform(get("/stores?sort=name")).andExpect(status().isOk());

    assertThat(pageableCaptor.getValue().getSort().getOrderFor("id")).isNotNull();
  }

  // 更新は全項目の置換。必須項目を欠く要求を通すと既存値が null で潰れるため、
  // ハンドラ引数の検査（@Valid）が効いていることを固定する。
  @Test
  @DisplayName("PUT /stores/{id} は name と short_name の要求を受け付けて 204 を返すこと")
  @WithMockUser(authorities = "PERM_STORE_MANAGE")
  void updateAcceptsNameAndShortName() throws Exception {
    mockMvc
        .perform(
            put("/stores/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"店舗\", \"short_name\": \"店\"}"))
        .andExpect(status().isNoContent());

    verify(storeRegistryService).update(eq("1"), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\u3000\u3000", "ABCD", "新宿西口"})
  @DisplayName("略称が空・空白のみ・4 文字以上なら 400 と日本語の検証メッセージを返すこと")
  @WithMockUser(authorities = "PERM_STORE_MANAGE")
  void rejectsInvalidShortName(String shortName) throws Exception {
    mockMvc
        .perform(
            put("/stores/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"店舗\", \"short_name\": \"" + shortName + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.short_name").value(containsString("略称")));
  }

  @Test
  @DisplayName("short_name を欠く要求は 400 になること")
  @WithMockUser(authorities = "PERM_STORE_MANAGE")
  void rejectsMissingShortName() throws Exception {
    mockMvc
        .perform(
            put("/stores/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"店舗\"}"))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"新宿西", "A", "𠮷𠮷𠮷", " 新宿 ", "\u3000新\u3000", "\u00A0新宿西\u00A0"})
  @DisplayName("全角・半角とも 1 文字、前後の空白は除いて数えること")
  @WithMockUser(authorities = "PERM_STORE_MANAGE")
  void acceptsShortNameCountedByCodePointAfterTrim(String shortName) throws Exception {
    mockMvc
        .perform(
            put("/stores/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"店舗\", \"short_name\": \"" + shortName + "\"}"))
        .andExpect(status().isNoContent());
  }

  // 作成応答が id を載せるのは、呼出側が一覧を引き直さずに新しい店舗を名指しできるようにするため。
  @Test
  @DisplayName("POST /stores は 201 で新規 id を返すこと")
  @WithMockUser(authorities = "PERM_STORE_MANAGE")
  void createRespondsWithCreatedAndTheNewId() throws Exception {
    when(storeRegistryService.create(any())).thenReturn(42L);

    mockMvc
        .perform(
            post("/stores")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"新店舗\", \"short_name\": \"新店\", \"domain\": \"new-store.kizuna.test\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(42));
  }
}
