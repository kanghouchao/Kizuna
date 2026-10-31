package com.kizuna.memberwithdrawal.api.me;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.member.domain.MemberAlreadyWithdrawnException;
import com.kizuna.memberwithdrawal.application.MemberWithdrawalService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 退会の入口が会員本人にだけ開き、理由の無い要求を 400、再退会を 409 で返すことの単体テスト。 */
@WebMvcTest(MemberWithdrawalController.class)
@Import({
  MemberWithdrawalControllerTest.MethodSecurityConfig.class,
  CommonExceptionHandler.class,
  StoreScopeHolder.class
})
class MemberWithdrawalControllerTest {

  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final String PATH = "/me/member/withdrawal";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MemberWithdrawalService memberWithdrawalService;
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("会員は理由を添えて退会でき、本文の無い 204 が返ること")
  void memberCanWithdraw() throws Exception {
    mockMvc
        .perform(request("{\"reason\":\"引っ越しのため\"}").with(member()))
        .andExpect(status().isNoContent());

    verify(memberWithdrawalService).withdrawSelf(10L, "引っ越しのため");
  }

  @Test
  @DisplayName("理由が空なら 400 で、退会処理には到達しないこと")
  void blankReasonIsRejected() throws Exception {
    mockMvc
        .perform(request("{\"reason\":\"  \"}").with(member()))
        .andExpect(status().isBadRequest());

    verify(memberWithdrawalService, never()).withdrawSelf(anyLong(), anyString());
  }

  @Test
  @DisplayName("退会済みの再退会は 409 で返ること")
  void secondWithdrawalConflicts() throws Exception {
    doThrow(new MemberAlreadyWithdrawnException())
        .when(memberWithdrawalService)
        .withdrawSelf(anyLong(), anyString());

    mockMvc.perform(request("{\"reason\":\"再度\"}").with(member())).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("会員以外（職員）は 403 で拒まれること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void staffIsForbidden() throws Exception {
    mockMvc.perform(request("{\"reason\":\"理由\"}")).andExpect(status().isForbidden());
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  /**
   * 検証済み Bearer トークンの会員。対象はトークンの利用者 ID で固定される（subject の電子メールは退会で後継へ移りうる）。 MVC 単体の文脈には security
   * filter が無いので、認可（SecurityContext）と handler の Principal の両方へ同じ認証を置く。
   */
  private static RequestPostProcessor member() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("member@example.com")
            .claim("userId", 10L)
            .build();
    var authentication =
        new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
    return request -> {
      SecurityContextHolder.getContext().setAuthentication(authentication);
      request.setUserPrincipal(authentication);
      return request;
    };
  }

  private static MockHttpServletRequestBuilder request(String body) {
    return post(PATH).contentType(MediaType.APPLICATION_JSON).content(body);
  }
}
