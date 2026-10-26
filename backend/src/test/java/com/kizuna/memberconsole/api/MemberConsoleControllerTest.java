package com.kizuna.memberconsole.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.member.application.MemberStatusFilter;
import com.kizuna.member.domain.MemberAlreadyWithdrawnException;
import com.kizuna.memberconsole.api.dto.MemberDetailResponse;
import com.kizuna.memberconsole.api.dto.MemberSummaryResponse;
import com.kizuna.memberconsole.application.MemberConsoleService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 会員コンソールの入口が MEMBER_VIEW / MEMBER_WITHDRAW で仕切られ、状態の既定・検索語・店舗集合・操作者を service へ渡すことの単体テスト。 */
@WebMvcTest(MemberConsoleController.class)
@Import({
  MemberConsoleControllerTest.MethodSecurityConfig.class,
  CommonExceptionHandler.class,
  StoreScopeHolder.class
})
class MemberConsoleControllerTest {

  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final long STAFF_USER_ID = 42L;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MemberConsoleService memberConsoleService;
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreScopeResolver storeScopeResolver;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("MEMBER_VIEW が無い職員には一覧も詳細も 403 で、service に到達しないこと")
  void listAndDetailRequireMemberView() throws Exception {
    mockMvc
        .perform(get("/members").with(staff("PERM_STAFF_MANAGE")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/members/7").with(staff("PERM_STAFF_MANAGE")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/members/7/point-entries").with(staff("PERM_STAFF_MANAGE")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(memberConsoleService);
  }

  @Test
  @DisplayName("一覧の状態の既定は有効（未退会）で、検索語は無しとして渡ること")
  void listDefaultsToActive() throws Exception {
    when(memberConsoleService.list(any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(summary(1L, null))));

    mockMvc
        .perform(get("/members").with(staff("PERM_MEMBER_VIEW")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].member_code").value("123456789012"));

    verify(memberConsoleService).list(eq(MemberStatusFilter.ACTIVE), eq(null), any(Pageable.class));
  }

  @Test
  @DisplayName("状態と検索語を指定すると、そのまま service へ渡ること")
  void listPassesStatusAndSearch() throws Exception {
    when(memberConsoleService.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/members?status=WITHDRAWN&search=hanako@example.com")
                .with(staff("PERM_MEMBER_VIEW")))
        .andExpect(status().isOk());

    verify(memberConsoleService)
        .list(eq(MemberStatusFilter.WITHDRAWN), eq("hanako@example.com"), any(Pageable.class));
  }

  @Test
  @DisplayName("目録に無い状態は束縛の段階で 400 になること")
  void unknownStatusIsRejected() throws Exception {
    mockMvc
        .perform(get("/members?status=DELETED").with(staff("PERM_MEMBER_VIEW")))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(memberConsoleService);
  }

  @Test
  @DisplayName("詳細は要求ごとに解決した授権店舗集合を service へ渡すこと")
  void detailPassesResolvedStoreScope() throws Exception {
    when(storeScopeResolver.resolve(any())).thenReturn(new StoreScope(false, Set.of(3L)));
    ArgumentCaptor<StoreScope> scope = ArgumentCaptor.forClass(StoreScope.class);
    when(memberConsoleService.detail(eq(7L), scope.capture())).thenReturn(detail());

    mockMvc
        .perform(get("/members/7").with(staff("PERM_MEMBER_VIEW")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.member_code").value("123456789012"))
        .andExpect(jsonPath("$.other_store_link_count").value(2));

    assertThat(scope.getValue().storeIds()).containsExactly(3L);
  }

  @Test
  @DisplayName("店舗集合を解決できない主体の詳細は 403 で、service に到達しないこと")
  void detailWithoutScopeIsForbidden() throws Exception {
    when(storeScopeResolver.resolve(any())).thenReturn(null);

    mockMvc
        .perform(get("/members/7").with(staff("PERM_MEMBER_VIEW")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(memberConsoleService);
  }

  @Test
  @DisplayName("存在しない会員の詳細は 404 であること")
  void detailOfMissingMemberIsNotFound() throws Exception {
    when(storeScopeResolver.resolve(any())).thenReturn(new StoreScope(true, Set.of()));
    when(memberConsoleService.detail(anyLong(), any()))
        .thenThrow(new NotFoundException("会員が見つかりません"));

    mockMvc
        .perform(get("/members/999").with(staff("PERM_MEMBER_VIEW")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("明細は cursor と size を service へ渡すこと")
  void pointEntriesPassCursorAndSize() throws Exception {
    when(memberConsoleService.entries(7L, "abc", 5))
        .thenReturn(new com.kizuna.shared.web.CursorPage<>(List.of(), null));

    mockMvc
        .perform(get("/members/7/point-entries?cursor=abc&size=5").with(staff("PERM_MEMBER_VIEW")))
        .andExpect(status().isOk());

    verify(memberConsoleService).entries(7L, "abc", 5);
  }

  @Test
  @DisplayName("MEMBER_WITHDRAW を持つ職員は理由を添えて代行退会でき、操作者はトークンの利用者 ID であること")
  void staffWithMemberWithdrawCanWithdraw() throws Exception {
    mockMvc
        .perform(withdrawal("{\"reason\":\"本人からの電話依頼\"}").with(staff("PERM_MEMBER_WITHDRAW")))
        .andExpect(status().isNoContent());

    verify(memberConsoleService).withdraw(7L, STAFF_USER_ID, "本人からの電話依頼");
  }

  @Test
  @DisplayName("MEMBER_VIEW だけの職員の代行退会は 403 で、service に到達しないこと")
  void memberViewOnlyCannotWithdraw() throws Exception {
    mockMvc
        .perform(withdrawal("{\"reason\":\"理由\"}").with(staff("PERM_MEMBER_VIEW")))
        .andExpect(status().isForbidden());

    verify(memberConsoleService, never()).withdraw(anyLong(), anyLong(), anyString());
  }

  @Test
  @DisplayName("理由が空なら 400 で、退会処理には到達しないこと")
  void blankReasonIsRejected() throws Exception {
    mockMvc
        .perform(withdrawal("{\"reason\":\"  \"}").with(staff("PERM_MEMBER_WITHDRAW")))
        .andExpect(status().isBadRequest());

    verify(memberConsoleService, never()).withdraw(anyLong(), anyLong(), anyString());
  }

  @Test
  @DisplayName("退会済みの再退会は 409 で返ること")
  void secondWithdrawalConflicts() throws Exception {
    doThrow(new MemberAlreadyWithdrawnException())
        .when(memberConsoleService)
        .withdraw(anyLong(), anyLong(), anyString());

    mockMvc
        .perform(withdrawal("{\"reason\":\"再度\"}").with(staff("PERM_MEMBER_WITHDRAW")))
        .andExpect(status().isConflict());
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      withdrawal(String body) {
    return post("/members/7/withdrawal")
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  /** 検証済み Bearer トークンの職員。認可（SecurityContext）と handler の Principal の両方へ同じ認証を置く。 */
  private static RequestPostProcessor staff(String... authorities) {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("staff@example.com")
            .claim("userId", STAFF_USER_ID)
            .build();
    var authentication =
        new JwtAuthenticationToken(
            jwt, java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    return request -> {
      SecurityContextHolder.getContext().setAuthentication(authentication);
      request.setUserPrincipal(authentication);
      return request;
    };
  }

  private static MemberSummaryResponse summary(long id, OffsetDateTime withdrawnAt) {
    return new MemberSummaryResponse(
        id,
        "123456789012",
        "会員 花子",
        "hanako@example.com",
        "BRONZE",
        OffsetDateTime.now(),
        withdrawnAt);
  }

  private static MemberDetailResponse detail() {
    return new MemberDetailResponse(
        7L,
        "123456789012",
        "会員 花子",
        "hanako@example.com",
        false,
        "SILVER",
        OffsetDateTime.now(),
        null,
        300L,
        List.of(),
        List.of(),
        List.of(),
        2L);
  }
}
