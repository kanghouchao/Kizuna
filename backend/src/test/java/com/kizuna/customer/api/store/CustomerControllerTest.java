package com.kizuna.customer.api.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.customer.api.dto.CustomerResponse;
import com.kizuna.customer.api.dto.CustomerSummaryResponse;
import com.kizuna.customer.application.CustomerService;
import com.kizuna.settings.application.SystemConfigService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** ハンドラ層で閉じている約束（offset ページングの全順序、生成 201 / 削除 204）の単体テスト。 */
@WebMvcTest(CustomerController.class)
@Import({CustomerControllerTest.MethodSecurityConfig.class, StoreContext.class})
class CustomerControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CustomerService customerService;

  // MaintenanceModeInterceptor / StoreExistenceInterceptor は HandlerInterceptor として
  // @WebMvcTest に自動で取り込まれるため、その依存もモックで満たす必要がある。
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreActivationService storeActivationService;

  @Test
  @DisplayName("GET /store/customers?sort=name でも id が副キーとして補われること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void listAppendsIdTiebreakerWhenCallerOverridesSort() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    Page<CustomerSummaryResponse> empty = new PageImpl<>(List.of());
    when(customerService.list(any(), any(), any(), pageableCaptor.capture())).thenReturn(empty);

    mockMvc
        .perform(
            get("/store/customers?sort=name").header("X-Role", "store").header("X-Store-ID", "1"))
        .andExpect(status().isOk());

    assertThat(pageableCaptor.getValue().getSort().getOrderFor("id")).isNotNull();
  }

  @Test
  @DisplayName("検索語の前後の空白は service へ渡る前に落ちること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void listTrimsSurroundingWhitespaceOfSearch() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<String> searchCaptor = ArgumentCaptor.forClass(String.class);
    Page<CustomerSummaryResponse> empty = new PageImpl<>(List.of());
    when(customerService.list(searchCaptor.capture(), any(), any(), any())).thenReturn(empty);

    mockMvc
        .perform(
            get("/store/customers")
                .param("search", " yamada ")
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk());

    assertThat(searchCaptor.getValue()).isEqualTo("yamada");
  }

  @Test
  @DisplayName("全角スペースだけの検索語も「指定なし」として service へ渡ること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void listPassesIdeographicSpaceOnlySearchAsNull() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<String> searchCaptor = ArgumentCaptor.forClass(String.class);
    Page<CustomerSummaryResponse> empty = new PageImpl<>(List.of());
    when(customerService.list(searchCaptor.capture(), any(), any(), any())).thenReturn(empty);

    mockMvc
        .perform(
            get("/store/customers")
                .param("search", "　　")
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk());

    assertThat(searchCaptor.getValue()).isNull();
  }

  @Test
  @DisplayName("POST /store/customers は 201 で生成された顧客を返すこと")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void createRespondsWithCreated() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(customerService.create(any())).thenReturn(CustomerResponse.builder().id("c1").build());

    mockMvc
        .perform(
            post("/store/customers")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"山田太郎\"}")
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("DELETE /store/customers/{id} は本体無しの 204 で返ること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void deleteRespondsWithNoContent() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            delete("/store/customers/c1")
                .with(csrf())
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isNoContent());
  }
}
