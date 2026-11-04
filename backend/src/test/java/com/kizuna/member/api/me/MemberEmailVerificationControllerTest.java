package com.kizuna.member.api.me;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.member.application.MemberEmailVerificationService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.TooManyRequestsException;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 検証メールの再送の入口が会員本人にだけ開き、202・400・429 で答えることの単体テスト。 */
@WebMvcTest(MemberEmailVerificationController.class)
@Import({
  MemberEmailVerificationControllerTest.MethodSecurityConfig.class,
  CommonExceptionHandler.class,
  StoreScopeHolder.class
})
class MemberEmailVerificationControllerTest {

  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final String PATH = "/me/email-verification";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MemberEmailVerificationService memberEmailVerificationService;
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("会員は本文なしで再送でき、トークンの利用者 ID が対象になり 202 が返ること")
  void memberCanResend() throws Exception {
    mockMvc.perform(post(PATH).with(member())).andExpect(status().isAccepted());

    verify(memberEmailVerificationService).resend(10L);
  }

  @Test
  @DisplayName("検証済みなら 400 で文言が返ること")
  void verifiedIsBadRequest() throws Exception {
    doThrow(new ServiceException("確認済み")).when(memberEmailVerificationService).resend(anyLong());

    mockMvc
        .perform(post(PATH).with(member()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("確認済み"));
  }

  @Test
  @DisplayName("上限に達していれば 429 で文言が返ること")
  void rateLimitedIsTooManyRequests() throws Exception {
    doThrow(new TooManyRequestsException("しばらく待って"))
        .when(memberEmailVerificationService)
        .resend(anyLong());

    mockMvc
        .perform(post(PATH).with(member()))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error").value("しばらく待って"));
  }

  @Test
  @DisplayName("職員は 403 で拒まれ、再送に到達しないこと")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void staffIsForbidden() throws Exception {
    mockMvc.perform(post(PATH)).andExpect(status().isForbidden());

    verify(memberEmailVerificationService, never()).resend(anyLong());
  }

  @Test
  @DisplayName("キャストは 403 で拒まれ、再送に到達しないこと")
  @WithMockUser(roles = "CAST")
  void castIsForbidden() throws Exception {
    mockMvc.perform(post(PATH)).andExpect(status().isForbidden());

    verify(memberEmailVerificationService, never()).resend(anyLong());
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  // MVC 単体の文脈には security filter が無いので、認可（SecurityContext）と handler の Principal の両方へ同じ認証を置く。
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
}
