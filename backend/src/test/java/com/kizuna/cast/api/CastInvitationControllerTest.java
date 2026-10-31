package com.kizuna.cast.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.auth.infrastructure.ExemptingBearerTokenResolver;
import com.kizuna.auth.infrastructure.JwtAuthenticationEntryPoint;
import com.kizuna.auth.infrastructure.JwtAuthoritiesConverter;
import com.kizuna.auth.infrastructure.SecurityConfig;
import com.kizuna.cast.api.dto.CastAcceptanceResponse;
import com.kizuna.cast.application.CastInvitationAcceptanceService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 既存の利用者による招待受諾の契約。本番の SecurityConfig と AuthenticationManager を載せ、資格情報の照合・CSRF・陳腐な Bearer
 * の扱いまで確かめる。
 */
@WebMvcTest(CastInvitationController.class)
@Import({
  SecurityConfig.class,
  JwtAuthoritiesConverter.class,
  JwtAuthenticationEntryPoint.class,
  ExemptingBearerTokenResolver.class,
  StoreScopeHolder.class
})
class CastInvitationControllerTest {

  private static final String EMAIL = "cast@kizuna.test";
  private static final String PASSWORD = "password123";

  @Autowired private MockMvc mockMvc;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private CastInvitationAcceptanceService acceptanceService;

  // 壊れた Bearer が免除されずに decoder まで届いたら 401 になるよう、検証失敗を返させる。
  @MockitoBean private JwtDecoder jwtDecoder;

  // 資格情報の照合は本番と同じ DaoAuthenticationProvider に通し、利用者の読み出しだけを差し替える。
  @MockitoBean private UserDetailsService userDetailsService;

  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreScopeResolver storeScopeResolver;

  @BeforeEach
  void setUp() {
    when(jwtDecoder.decode(any())).thenThrow(new BadJwtException("broken"));
    when(userDetailsService.loadUserByUsername(anyString()))
        .thenThrow(new UsernameNotFoundException("not found"));
  }

  private void givenAccount(boolean enabled) {
    doReturn(
            User.withUsername(EMAIL)
                .password(passwordEncoder.encode(PASSWORD))
                .authorities("ROLE_CAST")
                .disabled(!enabled)
                .build())
        .when(userDetailsService)
        .loadUserByUsername(EMAIL);
  }

  /** 匿名の受諾を模す。CSRF トークンも認証主体も載せない。 */
  private static MockHttpServletRequestBuilder acceptExisting(String email, String password) {
    return post("/cast-invitations/acceptance/existing")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"token\":\"invite-token\",\"email\":\"%s\",\"password\":\"%s\"}"
                .formatted(email, password));
  }

  @Test
  @DisplayName("正しい資格情報なら CSRF トークンなしの匿名要求で受諾でき、トークンも cookie も発行しないこと")
  void acceptsWithCredentialsAndIssuesNoCookie() throws Exception {
    givenAccount(true);
    when(acceptanceService.acceptAsExistingUser("invite-token", EMAIL))
        .thenReturn(new CastAcceptanceResponse("渋谷店"));

    mockMvc
        .perform(acceptExisting(EMAIL, PASSWORD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.store_name").value("渋谷店"))
        .andExpect(jsonPath("$.token").doesNotExist())
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
  }

  @Test
  @DisplayName("期限切れの Bearer が付いていても資格情報で受諾できること")
  void acceptsDespiteStaleBearer() throws Exception {
    givenAccount(true);
    when(acceptanceService.acceptAsExistingUser("invite-token", EMAIL))
        .thenReturn(new CastAcceptanceResponse("渋谷店"));

    mockMvc
        .perform(
            acceptExisting(EMAIL, PASSWORD).header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("パスワードが誤っていれば 401 で、招待には触れないこと")
  void rejectsWrongPasswordBeforeTouchingInvitation() throws Exception {
    givenAccount(true);

    mockMvc
        .perform(acceptExisting(EMAIL, "wrong-password"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("メールアドレスまたはパスワードが正しくありません"));

    verifyNoInteractions(acceptanceService);
  }

  @Test
  @DisplayName("停止中のアカウントは 401 で、招待には触れないこと")
  void rejectsDisabledAccount() throws Exception {
    givenAccount(false);

    mockMvc
        .perform(acceptExisting(EMAIL, PASSWORD))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("アカウントが無効化されています"));

    verifyNoInteractions(acceptanceService);
  }

  @Test
  @DisplayName("CAST でない利用者は 403 になること")
  void rejectsNonCastUser() throws Exception {
    givenAccount(true);
    when(acceptanceService.acceptAsExistingUser("invite-token", EMAIL))
        .thenThrow(new AccessDeniedException("CAST アカウントのみ既存受諾できます"));

    mockMvc.perform(acceptExisting(EMAIL, PASSWORD)).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("資格情報が欠けていれば 400 で、照合にも招待にも届かないこと")
  void rejectsMissingCredentials() throws Exception {
    mockMvc
        .perform(
            post("/cast-invitations/acceptance/existing")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"invite-token\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.email").exists())
        .andExpect(jsonPath("$.details.password").exists());

    verifyNoInteractions(acceptanceService);
  }
}
