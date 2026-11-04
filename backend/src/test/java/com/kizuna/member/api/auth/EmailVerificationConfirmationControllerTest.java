package com.kizuna.member.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.auth.infrastructure.ExemptingBearerTokenResolver;
import com.kizuna.auth.infrastructure.JwtAuthenticationEntryPoint;
import com.kizuna.auth.infrastructure.JwtAuthoritiesConverter;
import com.kizuna.auth.infrastructure.SecurityConfig;
import com.kizuna.member.application.EmailVerificationConfirmation;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** メールアドレスの確認端点の契約。本番の SecurityConfig を載せ、匿名の POST が陳腐な Bearer に阻まれないことまで確かめる。 */
@WebMvcTest(EmailVerificationConfirmationController.class)
@Import({
  SecurityConfig.class,
  JwtAuthoritiesConverter.class,
  JwtAuthenticationEntryPoint.class,
  ExemptingBearerTokenResolver.class,
  StoreScopeHolder.class
})
class EmailVerificationConfirmationControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private EmailVerificationConfirmation confirmation;

  // 壊れた Bearer が免除されずに decoder まで届いたら 401 になるよう、検証失敗を返させる。
  @MockitoBean private JwtDecoder jwtDecoder;

  // 無いと SecurityConfig の AuthenticationManager が自身を親に委ね、Bearer の認証失敗が StackOverflowError になる。
  @MockitoBean private UserDetailsService userDetailsService;

  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreScopeResolver storeScopeResolver;

  @BeforeEach
  void setUp() {
    when(jwtDecoder.decode(any())).thenThrow(new BadJwtException("broken"));
  }

  private static MockHttpServletRequestBuilder confirm(String body) {
    return post("/auth/email-verification/confirmation")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  @Test
  @DisplayName("匿名の確認は本文のトークンを渡して 204 を返すこと")
  void confirmsAnonymously() throws Exception {
    mockMvc
        .perform(confirm("{\"token\":\"verification-token\"}"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    verify(confirmation).confirm("verification-token");
  }

  @Test
  @DisplayName("壊れた Bearer が付いていても 204 を返すこと")
  void confirmsDespiteStaleBearer() throws Exception {
    mockMvc
        .perform(
            confirm("{\"token\":\"verification-token\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
        .andExpect(status().isNoContent());

    verify(confirmation).confirm("verification-token");
  }

  @Test
  @DisplayName("拒否されたトークンは 400 で、確認側の文言をそのまま返すこと")
  void rejectedTokenIsBadRequest() throws Exception {
    doThrow(new ServiceException("確認リンクが無効か、有効期限が切れています。"))
        .when(confirmation)
        .confirm("used-token");

    mockMvc
        .perform(confirm("{\"token\":\"used-token\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("確認リンクが無効か、有効期限が切れています。"));
  }

  @Test
  @DisplayName("トークンの無い本文は確認に届かず 400 になること")
  void blankTokenIsBadRequest() throws Exception {
    mockMvc.perform(confirm("{\"token\":\"\"}")).andExpect(status().isBadRequest());

    verifyNoInteractions(confirmation);
  }
}
