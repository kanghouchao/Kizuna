package com.kizuna.review.api;

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

import com.kizuna.review.application.ReviewService;
import com.kizuna.review.domain.ReviewChange;
import com.kizuna.review.domain.ReviewSearch;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
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
@WebMvcTest(ReviewController.class)
@Import({ReviewControllerTest.MethodSecurityConfig.class, StoreScopeHolder.class})
class ReviewControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final Long STORE_ID = 7L;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ReviewService reviews;

  // CurrentStoreArgumentResolver（引数解決器）は @WebMvcTest に自動で取り込まれるため、
  // その依存もモックで満たす必要がある。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  /** handler は Authentication を要求の principal から受けるため、模擬利用者を principal にも載せる。 */
  private static Authentication currentUser() {
    return SecurityContextHolder.getContext().getAuthentication();
  }

  @Test
  @DisplayName("口コミ詳細は名乗られた店舗の id で service を呼ぶこと")
  @WithMockUser(authorities = "PERM_REVIEW_VIEW")
  void detailPassesTheCurrentStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            get("/reviews/101").header("X-Store-ID", STORE_ID.toString()).principal(currentUser()))
        .andExpect(status().isOk());

    verify(reviews).detail(STORE_ID, "user", "101");
  }

  @Test
  @DisplayName("口コミの審査は名乗られた店舗の id で service を呼ぶこと")
  @WithMockUser(authorities = {"PERM_REVIEW_VIEW", "PERM_REVIEW_MODERATE"})
  void decisionPassesTheCurrentStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            post("/reviews/101/decisions")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser())
                .contentType("application/json")
                .content(
                    "{\"version\":0,\"decision\":\"APPROVE\",\"reason\":\"確認\",\"dedupe_key\":\"k\"}"))
        .andExpect(status().isOk());

    verify(reviews).change(eq(STORE_ID), eq("user"), any(ReviewChange.class));
  }

  @Test
  @DisplayName("口コミの履歴は新しい順の Page で返すこと")
  @WithMockUser(authorities = "PERM_REVIEW_VIEW")
  void historyIsAPageNewestFirst() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    when(reviews.history(eq(STORE_ID), eq("user"), eq("101"), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/reviews/101/history")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
  }

  @Test
  @DisplayName("口コミ一覧は order の並びで Page を返し、sort 引数を問い合わせに渡さないこと")
  @WithMockUser(authorities = "PERM_REVIEW_VIEW")
  void listOrdersByTheOrderParameterNotTheRequestSort() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<ReviewSearch> search = ArgumentCaptor.forClass(ReviewSearch.class);
    when(reviews.list(eq(STORE_ID), eq("user"), search.capture()))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/reviews")
                .param("sort", "CREATED_ASC")
                .param("page", "1")
                .param("size", "0")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    assertThat(search.getValue().pageable())
        .isEqualTo(PageRequest.of(1, 20, Sort.by(Sort.Direction.ASC, "createdAt", "id")));

    mockMvc
        .perform(
            get("/reviews")
                .param("page", "99999999")
                .param("size", "100")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isBadRequest());
  }
}
