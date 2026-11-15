package com.kizuna.order.api.me;

import static com.kizuna.shared.web.JwtRequests.jwtUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.api.dto.OrderMapperImpl;
import com.kizuna.order.application.MemberOrderApplicationResult;
import com.kizuna.order.application.MemberOrderApplicationService;
import com.kizuna.order.domain.OrderApplication;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 会員本人の予約申請経路が会員にだけ開いていることの単体テスト。 */
@WebMvcTest(MemberOrderApplicationController.class)
@Import({
  MemberOrderApplicationControllerTest.MethodSecurityConfig.class,
  OrderMapperImpl.class,
  StoreScopeHolder.class
})
class MemberOrderApplicationControllerTest {

  private static final long USER_ID = 7L;

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static MemberOrderApplicationResult emptyResult() {
    return new MemberOrderApplicationResult(null, null, null, null, null, null, null, null, false);
  }

  private static final String BODY =
      """
      {"store_id":1,"business_date":"2999-01-01","pax":2,"declared_name":"名乗り太郎"}
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MemberOrderApplicationService memberOrderApplicationService;

  // CurrentStoreArgumentResolver（引数解決器）は @WebMvcTest に自動で取り込まれるため、
  // その依存もモックで満たす必要がある。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("会員は予約の申請・一覧・取り下げができること")
  void memberCanUseOwnApplicationRoutes() throws Exception {
    when(memberOrderApplicationService.request(anyLong(), any())).thenReturn(emptyResult());
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    when(memberOrderApplicationService.list(anyLong(), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of()));
    when(memberOrderApplicationService.withdraw(anyLong(), anyString())).thenReturn(emptyResult());

    mockMvc
        .perform(
            memberPost("/me/order-applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .with(jwtUser(USER_ID, "ROLE_MEMBER")))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get("/me/order-applications").with(jwtUser(USER_ID, "ROLE_MEMBER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    assertThat(pageable.getValue().getSort())
        .isEqualTo(
            Sort.by(
                Sort.Direction.DESC, OrderApplication::getBusinessDate, OrderApplication::getId));
    mockMvc
        .perform(
            memberPost("/me/order-applications/a1/withdrawal")
                .with(jwtUser(USER_ID, "ROLE_MEMBER")))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("キャストは会員の予約申請経路に到達できないこと")
  void castIsRejected() throws Exception {
    mockMvc
        .perform(get("/me/order-applications").with(jwtUser(USER_ID, "ROLE_CAST")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("受注管理権限を持つスタッフでも会員の予約申請経路に到達できないこと")
  void staffIsRejected() throws Exception {
    mockMvc
        .perform(get("/me/order-applications").with(jwtUser(USER_ID, "PERM_ORDER_MANAGE")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("匿名では会員の予約申請経路に到達できないこと")
  @WithAnonymousUser
  void anonymousIsRejected() throws Exception {
    mockMvc.perform(get("/me/order-applications")).andExpect(status().isUnauthorized());
  }

  private MockHttpServletRequestBuilder memberPost(String path) {
    return post(path);
  }
}
