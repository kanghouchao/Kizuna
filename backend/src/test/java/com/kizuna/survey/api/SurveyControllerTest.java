package com.kizuna.survey.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.survey.api.dto.SurveyOperationResponse;
import com.kizuna.survey.api.dto.SurveyRevisionWriteResponse;
import com.kizuna.survey.application.SurveyReadService;
import com.kizuna.survey.application.SurveyService;
import com.kizuna.survey.domain.SurveyCommand;
import com.kizuna.survey.domain.SurveyValues.Operation;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** handler が X-Store-ID で名乗られた店舗をそのまま service へ渡すことの単体テスト。 */
@WebMvcTest({SurveyController.class, SurveyResponseController.class})
@Import({SurveyControllerTest.MethodSecurityConfig.class, StoreScopeHolder.class})
class SurveyControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final Long STORE_ID = 7L;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SurveyService service;
  @MockitoBean private SurveyReadService reads;

  // CurrentStoreArgumentResolver（引数解決器）は @WebMvcTest に自動で取り込まれるため、
  // その依存もモックで満たす必要がある。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  /** handler は Authentication を要求の principal から受けるため、模擬利用者を principal にも載せる。 */
  private static Authentication currentUser() {
    return SecurityContextHolder.getContext().getAuthentication();
  }

  @Test
  @DisplayName("アンケート一覧は名乗られた店舗の id で service を呼ぶこと")
  @WithMockUser(authorities = "PERM_SURVEY_VIEW")
  void listPassesTheCurrentStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(get("/surveys").header("X-Store-ID", STORE_ID.toString()).principal(currentUser()))
        .andExpect(status().isOk());

    verify(reads)
        .list(
            STORE_ID,
            "user",
            PageRequest.of(0, 20, Sort.by("id")),
            null,
            null,
            null,
            "CREATED_DESC");
  }

  @Test
  @DisplayName("アンケート作成は名乗られた店舗の id で service を呼ぶこと")
  @WithMockUser(authorities = {"PERM_SURVEY_VIEW", "PERM_SURVEY_MANAGE"})
  void createPassesTheCurrentStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(service.write(eq(STORE_ID), eq("user"), any()))
        .thenReturn(
            new SurveyRevisionWriteResponse(
                null, new SurveyOperationResponse("op", Operation.DRAFT_CREATED, "r1", 0L, false)));

    mockMvc
        .perform(
            post("/surveys")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser())
                .contentType("application/json")
                .content(
                    """
                    {"title":"利用後アンケート","questions":[{"question_key":"q1","type":"TEXT","prompt":"ご意見","required":true,"options":[]}],"dedupe_key":"create"}
                    """))
        .andExpect(status().isCreated());

    verify(service).write(eq(STORE_ID), eq("user"), any(SurveyCommand.Create.class));
  }

  @Test
  @DisplayName("回答詳細は名乗られた店舗の id で service を呼ぶこと")
  @WithMockUser(authorities = "PERM_SURVEY_VIEW")
  void answerPassesTheCurrentStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            get("/survey-responses/101")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isOk());

    verify(service).answer(STORE_ID, "user", "101");
  }

  @Test
  @DisplayName("改訂と回答の履歴は新しい順の Page で返すこと")
  @WithMockUser(authorities = "PERM_SURVEY_VIEW")
  void historiesArePagesNewestFirst() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<Pageable> revisions = ArgumentCaptor.forClass(Pageable.class);
    ArgumentCaptor<Pageable> answers = ArgumentCaptor.forClass(Pageable.class);
    when(reads.revisionHistory(eq(STORE_ID), eq("user"), eq("s1"), eq("r1"), revisions.capture()))
        .thenReturn(new PageImpl<>(List.of()));
    when(reads.answerHistory(eq(STORE_ID), eq("user"), eq("101"), answers.capture()))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/surveys/s1/revisions/r1/history")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    mockMvc
        .perform(
            get("/survey-responses/101/history")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    Sort newestFirst = Sort.by(Sort.Direction.DESC, "id");
    assertThat(revisions.getValue().getSort()).isEqualTo(newestFirst);
    assertThat(answers.getValue().getSort()).isEqualTo(newestFirst);
  }

  @Test
  @DisplayName("設問版と回答の一覧は Page で返し、範囲外のページを 400 にすること")
  @WithMockUser(authorities = "PERM_SURVEY_VIEW")
  void revisionAndAnswerListsArePages() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<Pageable> revisions = ArgumentCaptor.forClass(Pageable.class);
    when(reads.revisions(eq(STORE_ID), eq("user"), eq("s1"), revisions.capture()))
        .thenReturn(new PageImpl<>(List.of()));
    ArgumentCaptor<Pageable> answers = ArgumentCaptor.forClass(Pageable.class);
    when(reads.answers(
            eq(STORE_ID),
            eq("user"),
            eq("s1"),
            eq("r1"),
            answers.capture(),
            eq(null),
            eq(null),
            eq("RECEIVED_ASC")))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/surveys/s1/revisions")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    mockMvc
        .perform(
            get("/surveys/s1/revisions/r1/responses")
                .param("sort", "RECEIVED_ASC")
                .param("page", "2")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    mockMvc
        .perform(
            get("/surveys/s1/revisions")
                .param("page", "99999999")
                .param("size", "100")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isBadRequest());

    assertThat(revisions.getValue())
        .isEqualTo(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "revisionNumber", "id")));
    assertThat(answers.getValue().getPageNumber()).isEqualTo(2);
    assertThat(answers.getValue().getPageSize()).isEqualTo(20);
  }
}
