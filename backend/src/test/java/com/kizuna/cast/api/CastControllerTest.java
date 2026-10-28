package com.kizuna.cast.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.cast.api.dto.CastInvitationResponse;
import com.kizuna.cast.api.dto.CastSummaryResponse;
import com.kizuna.cast.application.CastEnrollmentService;
import com.kizuna.cast.application.CastInvitationService;
import com.kizuna.cast.application.CastService;
import com.kizuna.cast.domain.CastPublicationStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** ハンドラ層で閉じている約束（offset ページングの全順序、削除 204）の単体テスト。 */
@WebMvcTest(CastController.class)
@Import({CastControllerTest.MethodSecurityConfig.class, StoreScopeHolder.class})
class CastControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CastService castService;
  @MockitoBean private CastEnrollmentService enrollmentService;
  @MockitoBean private CastInvitationService castInvitationService;

  // CurrentStoreArgumentResolver（引数解決器）は @WebMvcTest に自動で取り込まれるため、
  // その依存もモックで満たす必要がある。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("GET /casts?sort=name でも id が副キーとして補われること")
  @WithMockUser(authorities = "PERM_CAST_MANAGE")
  void listAppendsIdTiebreakerWhenCallerOverridesSort() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    Page<CastSummaryResponse> empty = new PageImpl<>(List.of());
    when(castService.list(eq(1L), any(), pageableCaptor.capture())).thenReturn(empty);

    mockMvc.perform(get("/casts?sort=name").header("X-Store-ID", "1")).andExpect(status().isOk());

    assertThat(pageableCaptor.getValue().getSort().getOrderFor("id")).isNotNull();
  }

  @Test
  @DisplayName("空白だけの検索語は「指定なし」として service へ渡ること")
  @WithMockUser(authorities = "PERM_CAST_MANAGE")
  void listPassesBlankSearchAsNull() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<String> searchCaptor = ArgumentCaptor.forClass(String.class);
    Page<CastSummaryResponse> empty = new PageImpl<>(List.of());
    when(castService.list(eq(1L), searchCaptor.capture(), any())).thenReturn(empty);

    mockMvc
        .perform(get("/casts").param("search", "  ").header("X-Store-ID", "1"))
        .andExpect(status().isOk());

    assertThat(searchCaptor.getValue()).isNull();
  }

  @Test
  @DisplayName("POST /casts/{id}/invitation は操作者のメールを監査の主体として service へ渡すこと")
  @WithMockUser(username = "operator@example.test", authorities = "PERM_CAST_INVITE")
  void issueInvitationPassesTheOperatorAsAuditActor() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(castInvitationService.issue(1L, "cast-1", "operator@example.test"))
        .thenReturn(new CastInvitationResponse("token", OffsetDateTime.now()));

    mockMvc
        .perform(
            post("/casts/cast-1/invitation")
                .with(csrf())
                .principal(() -> "operator@example.test")
                .header("X-Store-ID", "1"))
        .andExpect(status().isCreated());

    verify(castInvitationService).issue(1L, "cast-1", "operator@example.test");
  }

  @Test
  @DisplayName("DELETE /casts/{id} は本体無しの 204 で返ること")
  @WithMockUser(authorities = "PERM_CAST_MANAGE")
  void deleteRespondsWithNoContent() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(delete("/casts/cast-1").with(csrf()).header("X-Store-ID", "1"))
        .andExpect(status().isNoContent());

    verify(castService).delete(1L, "cast-1");
  }

  @Test
  @DisplayName("公開状態の切替はキャスト管理権限で操作できること")
  @WithMockUser(authorities = "PERM_CAST_MANAGE")
  void publicationCanBeChanged() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/casts/cast-1/publication")
                .with(csrf())
                .header("X-Store-ID", "1")
                .contentType("application/json")
                .content("{\"publication_status\":\"PUBLISHED\"}"))
        .andExpect(status().isOk());

    verify(castService).changePublication(1L, "cast-1", CastPublicationStatus.PUBLISHED);
  }

  @Test
  @WithMockUser(authorities = "PERM_CAST_MANAGE")
  void publicationRejectsMissingAndUnknownStatus() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    for (String body : List.of("{}", "{\"publication_status\":\"ACTIVE\"}")) {
      mockMvc
          .perform(
              MockMvcRequestBuilders.patch("/casts/cast-1/publication")
                  .with(csrf())
                  .header("X-Store-ID", "1")
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  @WithMockUser(authorities = "PERM_CAST_INVITE")
  void publicationRequiresManagePermission() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/casts/cast-1/publication")
                .with(csrf())
                .header("X-Store-ID", "1")
                .contentType("application/json")
                .content("{\"publication_status\":\"PUBLISHED\"}"))
        .andExpect(status().isForbidden());
  }
}
