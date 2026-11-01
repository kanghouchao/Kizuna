package com.kizuna.customer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.customer.api.dto.CustomerMemberLinkResponse;
import com.kizuna.customer.application.CustomerMemberLinkService;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.time.OffsetDateTime;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 会員紐づけ端点の授権と契約の単体テスト。
 *
 * <p>現況の読み口は「紐づいていない」を本体で表さず 404 で返す。呼出側が状態の有無だけで操作の可否を決められる形を、 200 と 404 の両側で固定する。
 */
@WebMvcTest(CustomerMemberLinkController.class)
@Import({
  CustomerMemberLinkControllerTest.MethodSecurityConfig.class,
  StoreScopeHolder.class,
  CommonExceptionHandler.class
})
class CustomerMemberLinkControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mockMvc;

  private static final Long STORE_ID = 7L;

  @MockitoBean private CustomerMemberLinkService customerMemberLinkService;

  // CurrentStoreArgumentResolver（引数解決器）は @WebMvcTest に自動で取り込まれるため、
  // その依存もモックで満たす必要がある。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("現況の照会は紐づけ済みなら会員コードを返すこと")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void currentReturnsTheMemberCodeWhenLinked() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(customerMemberLinkService.current(eq(STORE_ID), anyString()))
        .thenReturn(
            new CustomerMemberLinkResponse(
                "l1", true, "123456789012", OffsetDateTime.parse("2026-08-01T10:00:00+09:00")));

    mockMvc
        .perform(storeGet("/customers/c1/member-link"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.member_code").value("123456789012"))
        .andExpect(jsonPath("$.linked").value(true));
  }

  @Test
  @DisplayName("未紐づけの顧客の現況は 404 で返ること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void currentIsNotFoundWhenTheCustomerHasNoLink() throws Exception {
    // 「紐づいていない」を 200 の本体で表すと、呼出側が本体を読んでからもう一度分岐することになる
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(customerMemberLinkService.current(eq(STORE_ID), anyString()))
        .thenThrow(new NotFoundException("紐づけられている会員がいません"));

    mockMvc
        .perform(storeGet("/customers/c1/member-link"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.member_code").doesNotExist());
  }

  @Test
  @DisplayName("現況の照会は CUSTOMER_MANAGE を持たなければ拒否されること")
  @WithMockUser(authorities = "PERM_POINT_ADJUST")
  void currentIsRefusedWithoutCustomerManage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc.perform(storeGet("/customers/c1/member-link")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("履歴は新しい区間から並べたページで返ること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void historyIsAPageNewestFirst() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(customerMemberLinkService.history(eq(STORE_ID), eq("c1"), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc
        .perform(storeGet("/customers/c1/member-link/history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, "linkedAt", "id"));
  }

  @Test
  @DisplayName("解除は本体無しの 204 で返ること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void unlinkRespondsWithNoContent() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            post("/customers/c1/member-link/releases")
                .contentType("application/json")
                .content("{\"expected_link_id\":\"l1\",\"operation_reason\":\"本人依頼\"}")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(() -> "tanaka.hanako@kizuna.test"))
        .andExpect(status().isNoContent());

    verify(customerMemberLinkService)
        .unlink(STORE_ID, "c1", "l1", "本人依頼", "tanaka.hanako@kizuna.test");
  }

  private MockHttpServletRequestBuilder storeGet(String path) {
    return get(path)
        .header("X-Store-ID", STORE_ID.toString())
        .principal(() -> "tanaka.hanako@kizuna.test");
  }
}
