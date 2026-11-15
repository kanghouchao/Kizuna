package com.kizuna.cast.api;

import static com.kizuna.shared.web.JwtRequests.jwtUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.cast.api.dto.CastMapperImpl;
import com.kizuna.cast.application.CastCreateCommand;
import com.kizuna.cast.application.CastDetail;
import com.kizuna.cast.application.CastEnrollmentService;
import com.kizuna.cast.application.CastInvitationService;
import com.kizuna.cast.application.CastService;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentSnapshot;
import com.kizuna.cast.domain.CastEnrollmentStatus;
import com.kizuna.cast.domain.CastEnrollmentStatusHistory;
import com.kizuna.cast.domain.CastInvitation;
import com.kizuna.cast.domain.CastInvitationStatus;
import com.kizuna.cast.domain.CastProfile;
import com.kizuna.cast.domain.CastPublicationStatus;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.data.domain.Sort;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** ハンドラ層で閉じている約束（offset ページングの全順序・件数上限・取得位置の境界、削除 204）の単体テスト。 */
@WebMvcTest(CastController.class)
@Import({
  CastControllerTest.MethodSecurityConfig.class,
  StoreScopeHolder.class,
  CastMapperImpl.class
})
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

  @BeforeEach
  void authorizeStore() {
    when(storeScopeResolver.resolve(any())).thenReturn(new StoreScope(Set.of(1L)));
  }

  @Test
  @DisplayName("GET /casts?sort=name でも id が副キーとして補われること")
  void listAppendsIdTiebreakerWhenCallerOverridesSort() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    Page<CastDetail> empty = new PageImpl<>(List.of());
    when(castService.list(eq(1L), any(), pageableCaptor.capture())).thenReturn(empty);

    mockMvc
        .perform(
            get("/casts?sort=name")
                .with(jwtUser(42L, "PERM_CAST_MANAGE"))
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk());

    assertThat(pageableCaptor.getValue().getSort().getOrderFor("id")).isNotNull();
  }

  @Test
  @DisplayName("上限を超える取得件数は Pageable 共通の上限 100 件へ丸めて service へ渡ること")
  void listClampsSizeToSharedPageLimit() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(castService.list(eq(1L), any(), pageableCaptor.capture()))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/casts")
                .with(jwtUser(42L, "PERM_CAST_MANAGE"))
                .param("size", "1000")
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk());

    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
  }

  @Test
  @DisplayName("取得位置が int を超えるページ指定は service を呼ばずに 400 を返すこと")
  void listRejectsPageBeyondIntOffset() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            get("/casts")
                .with(jwtUser(42L, "PERM_CAST_MANAGE"))
                .param("page", "99999999")
                .param("size", "100")
                .header("X-Store-ID", "1"))
        .andExpect(status().isBadRequest());

    verify(castService, never()).list(anyLong(), any(), any());
  }

  @Test
  @DisplayName("在籍の状態履歴とスナップショットは記録の新しい順の Page で返すこと")
  void enrollmentHistoriesArePagesNewestFirst() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<Pageable> histories = ArgumentCaptor.forClass(Pageable.class);
    ArgumentCaptor<Pageable> snapshots = ArgumentCaptor.forClass(Pageable.class);
    when(enrollmentService.history(eq(1L), eq("e1"), histories.capture()))
        .thenReturn(new PageImpl<>(List.of()));
    when(enrollmentService.snapshots(eq(1L), eq("e1"), snapshots.capture()))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/casts/e1/status-histories")
                .with(jwtUser(42L, "PERM_CAST_MANAGE"))
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    mockMvc
        .perform(
            get("/casts/e1/snapshots")
                .with(jwtUser(42L, "PERM_CAST_MANAGE"))
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    assertThat(histories.getValue().getSort())
        .isEqualTo(
            Sort.by(
                Sort.Direction.DESC,
                CastEnrollmentStatusHistory::getRecordedAt,
                CastEnrollmentStatusHistory::getId));
    assertThat(snapshots.getValue().getSort())
        .isEqualTo(
            Sort.by(
                Sort.Direction.DESC,
                CastEnrollmentSnapshot::getRecordedAt,
                CastEnrollmentSnapshot::getId));
  }

  @Test
  @DisplayName("空白だけの検索語は「指定なし」として service へ渡ること")
  void listPassesBlankSearchAsNull() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<String> searchCaptor = ArgumentCaptor.forClass(String.class);
    Page<CastDetail> empty = new PageImpl<>(List.of());
    when(castService.list(eq(1L), searchCaptor.capture(), any())).thenReturn(empty);

    mockMvc
        .perform(
            get("/casts")
                .with(jwtUser(42L, "PERM_CAST_MANAGE"))
                .param("search", "  ")
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk());

    assertThat(searchCaptor.getValue()).isNull();
  }

  @Test
  @DisplayName("POST /casts/{id}/invitation は操作者の利用者 ID を監査の主体として service へ渡すこと")
  void issueInvitationPassesTheOperatorAsAuditActor() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(castInvitationService.issue(1L, "cast-1", 42L))
        .thenReturn(
            CastInvitation.builder().token("token").expiresAt(OffsetDateTime.now()).build());

    mockMvc
        .perform(
            post("/casts/cast-1/invitation")
                .with(jwtUser(42L, "PERM_CAST_INVITE"))
                .header("X-Store-ID", "1"))
        .andExpect(status().isCreated());

    verify(castInvitationService).issue(1L, "cast-1", 42L);
  }

  @Test
  @DisplayName("DELETE /casts/{id} は本体無しの 204 で返ること")
  void deleteRespondsWithNoContent() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            delete("/casts/cast-1")
                .with(jwtUser(42L, "PERM_CAST_MANAGE"))
                .header("X-Store-ID", "1"))
        .andExpect(status().isNoContent());

    verify(castService).delete(1L, "cast-1", 42L);
  }

  @Test
  @DisplayName("詳細は内部・公開のカスタムフィールド値を一つの custom_fields に合わせて返すこと")
  void detailCombinesInternalAndPublicCustomFields() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    CastEnrollment enrollment =
        CastEnrollment.builder()
            .status(CastEnrollmentStatus.ENROLLED)
            .customFields(Map.of("memo", "内部"))
            .build();
    enrollment.setId("e1");
    CastProfile profile =
        CastProfile.builder().name("花").customFields(Map.of("hobby", "読書")).build();
    when(castService.get(1L, "e1"))
        .thenReturn(new CastDetail(enrollment, profile, CastInvitationStatus.INVITED, false));

    mockMvc
        .perform(get("/casts/e1").with(jwtUser(42L, "PERM_CAST_MANAGE")).header("X-Store-ID", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("e1"))
        .andExpect(jsonPath("$.name").value("花"))
        .andExpect(jsonPath("$.custom_fields.memo").value("内部"))
        .andExpect(jsonPath("$.custom_fields.hobby").value("読書"))
        .andExpect(jsonPath("$.invitation_status").value("INVITED"))
        .andExpect(jsonPath("$.deletable").value(false));
  }

  @Test
  @DisplayName("公開状態の切替はキャスト管理権限で操作できること")
  void publicationCanBeChanged() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/casts/cast-1/publication")
                .with(jwtUser(42L, "PERM_CAST_MANAGE"))
                .header("X-Store-ID", "1")
                .contentType("application/json")
                .content("{\"publication_status\":\"PUBLISHED\"}"))
        .andExpect(status().isOk());

    verify(castService).changePublication(1L, "cast-1", CastPublicationStatus.PUBLISHED, 42L);
  }

  @Test
  void publicationRejectsMissingAndUnknownStatus() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    for (String body : List.of("{}", "{\"publication_status\":\"ACTIVE\"}")) {
      mockMvc
          .perform(
              MockMvcRequestBuilders.patch("/casts/cast-1/publication")
                  .with(jwtUser(42L, "PERM_CAST_MANAGE"))
                  .header("X-Store-ID", "1")
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  void publicationRequiresManagePermission() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/casts/cast-1/publication")
                .with(jwtUser(42L, "PERM_CAST_INVITE"))
                .header("X-Store-ID", "1")
                .contentType("application/json")
                .content("{\"publication_status\":\"PUBLISHED\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("キャスト作成は入店日を必須とし、未来日を受け付けること")
  void createRequiresStartedOn() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    mockMvc
        .perform(
            post("/casts")
                .with(jwtUser(42L, "PERM_CAST_MANAGE"))
                .header("X-Store-ID", "1")
                .contentType("application/json")
                .content("{\"name\":\"花\"}"))
        .andExpect(status().isBadRequest());
    verify(castService, never()).create(anyLong(), any(), anyLong());

    ArgumentCaptor<CastCreateCommand> captor = ArgumentCaptor.forClass(CastCreateCommand.class);
    when(castService.create(eq(1L), captor.capture(), eq(42L)))
        .thenReturn(
            new CastDetail(
                CastEnrollment.builder().status(CastEnrollmentStatus.ENROLLED).build(),
                new CastProfile(),
                null,
                true));
    mockMvc
        .perform(
            post("/casts")
                .with(jwtUser(42L, "PERM_CAST_MANAGE"))
                .header("X-Store-ID", "1")
                .contentType("application/json")
                .content("{\"name\":\"花\",\"started_on\":\"2099-04-01\"}"))
        .andExpect(status().isCreated());
    assertThat(captor.getValue().startedOn()).isEqualTo(LocalDate.of(2099, 4, 1));
  }
}
