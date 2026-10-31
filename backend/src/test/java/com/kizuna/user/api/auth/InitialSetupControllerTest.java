package com.kizuna.user.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.auth.infrastructure.ExemptingBearerTokenResolver;
import com.kizuna.auth.infrastructure.JwtAuthenticationEntryPoint;
import com.kizuna.auth.infrastructure.JwtAuthoritiesConverter;
import com.kizuna.auth.infrastructure.SecurityConfig;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.api.dto.InitialSetupRequest;
import com.kizuna.user.application.InitialSetupService;
import com.kizuna.user.application.StoreScopeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

/** 初期設定の公開端点の契約。本番の SecurityConfig を載せ、匿名の POST が陳腐な Bearer に阻まれないことまで確かめる。 */
@WebMvcTest(InitialSetupController.class)
@Import({
  SecurityConfig.class,
  JwtAuthoritiesConverter.class,
  JwtAuthenticationEntryPoint.class,
  ExemptingBearerTokenResolver.class,
  StoreScopeHolder.class
})
class InitialSetupControllerTest {

  private static final String BODY =
      "{\"email\":\"owner@kizuna.test\",\"password\":\"password123\",\"display_name\":\"管理者\"}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InitialSetupService initialSetupService;

  // 壊れた Bearer が免除されずに decoder まで届いたら 401 になるよう、検証失敗を返させる。
  @MockitoBean private JwtDecoder jwtDecoder;

  // 本番と同じく全域の AuthenticationManager に提供者を持たせる。無いと SecurityConfig が公開する同 Bean が
  // 自身を親に委ね、Bearer の認証失敗が 401 でなく StackOverflowError になる。
  @MockitoBean private UserDetailsService userDetailsService;

  // CurrentStoreArgumentResolver（引数解決器）は @WebMvcTest に自動で取り込まれるため、
  // その依存もモックで満たす必要がある。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreScopeResolver storeScopeResolver;

  @BeforeEach
  void setUp() {
    when(jwtDecoder.decode(any())).thenThrow(new BadJwtException("broken"));
  }

  /** 匿名の登録を模す。認証主体を載せない。 */
  private static MockHttpServletRequestBuilder anonymousPost(String body) {
    return post("/auth/initial-setup").contentType(MediaType.APPLICATION_JSON).content(body);
  }

  @Test
  @DisplayName("GET は匿名で要否を返すこと")
  void statusReturnsRequiredFlag() throws Exception {
    when(initialSetupService.required()).thenReturn(true);

    mockMvc
        .perform(get("/auth/initial-setup"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.required").value(true));
  }

  @Test
  @DisplayName("POST は匿名要求で受理され、201 で新しい id を返すこと")
  void registerAcceptsAnonymousPostWithoutCsrfToken() throws Exception {
    ArgumentCaptor<InitialSetupRequest> captor = ArgumentCaptor.forClass(InitialSetupRequest.class);
    when(initialSetupService.register(captor.capture())).thenReturn(42L);

    mockMvc
        .perform(anonymousPost(BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(42));

    assertThat(captor.getValue().getEmail()).isEqualTo("owner@kizuna.test");
    assertThat(captor.getValue().getPassword()).isEqualTo("password123");
    assertThat(captor.getValue().getDisplayName()).isEqualTo("管理者");
  }

  @Test
  @DisplayName("POST は壊れた Bearer が付いていても匿名として受理されること")
  void registerIgnoresStaleBearer() throws Exception {
    when(initialSetupService.register(any())).thenReturn(42L);

    mockMvc
        .perform(anonymousPost(BODY).header(HttpHeaders.AUTHORIZATION, "Bearer xxx"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(42));
  }

  @Test
  @DisplayName("完了後の POST は 404 になること")
  void registerIsNotFoundOnceCompleted() throws Exception {
    when(initialSetupService.register(any())).thenThrow(new NotFoundException("初期設定は完了しています"));

    mockMvc
        .perform(anonymousPost(BODY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("初期設定は完了しています"));
  }

  @Test
  @DisplayName("入力不正の POST は 400 で、サービスへ届かないこと")
  void registerRejectsInvalidInput() throws Exception {
    mockMvc
        .perform(
            anonymousPost(
                "{\"email\":\"not-an-email\",\"password\":\"short\",\"display_name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.email").exists())
        .andExpect(jsonPath("$.details.password").exists())
        .andExpect(jsonPath("$.details.display_name").exists());

    verifyNoInteractions(initialSetupService);
  }
}
