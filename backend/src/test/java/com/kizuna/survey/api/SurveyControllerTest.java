package com.kizuna.survey.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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

    verify(reads).list(STORE_ID, "user", 0, 20, null, null, null, "CREATED_DESC");
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
                .with(csrf())
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
}
