package com.kizuna.review.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.receipt.once.Receipt;
import com.kizuna.review.application.ReviewService;
import com.kizuna.review.application.ReviewViews;
import com.kizuna.review.domain.ReviewCommand;
import com.kizuna.review.domain.ReviewDetailsView;
import com.kizuna.review.domain.ReviewHistory;
import com.kizuna.review.domain.ReviewInput;
import com.kizuna.review.domain.ReviewRecord;
import com.kizuna.review.domain.ReviewSearch;
import com.kizuna.review.domain.ReviewValues.Operation;
import com.kizuna.review.domain.ReviewValues.ReceivedVia;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
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
    when(reviews.detail(STORE_ID, "user", "00000000-0000-7000-8000-000000000101"))
        .thenReturn(detail());

    mockMvc
        .perform(
            get("/reviews/00000000-0000-7000-8000-000000000101")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isOk());

    verify(reviews).detail(STORE_ID, "user", "00000000-0000-7000-8000-000000000101");
  }

  @Test
  @DisplayName("口コミの審査は名乗られた店舗の id で service を呼ぶこと")
  @WithMockUser(authorities = {"PERM_REVIEW_VIEW", "PERM_REVIEW_MODERATE"})
  void decisionPassesTheCurrentStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(reviews.write(eq(STORE_ID), eq("user"), any(ReviewCommand.Decide.class)))
        .thenReturn(
            new ReviewViews.Written(
                detail(),
                new Receipt(
                    "50",
                    Operation.APPROVED.receiptName(),
                    "00000000-0000-7000-8000-000000000101",
                    Map.of("committed_version", 1L),
                    false)));

    mockMvc
        .perform(
            post("/reviews/00000000-0000-7000-8000-000000000101/decisions")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser())
                .contentType("application/json")
                .content(
                    "{\"version\":0,\"decision\":\"APPROVE\",\"reason\":\"確認\",\"dedupe_key\":\"k\"}"))
        .andExpect(status().isOk());

    verify(reviews).write(eq(STORE_ID), eq("user"), any(ReviewCommand.Decide.class));
  }

  @Test
  @DisplayName("版を文字列で送った審査は全体の厳格な型照合で 400 になり service に届かないこと")
  @WithMockUser(authorities = {"PERM_REVIEW_VIEW", "PERM_REVIEW_MODERATE"})
  void decisionRejectsVersionSentAsString() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            post("/reviews/00000000-0000-7000-8000-000000000101/decisions")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser())
                .contentType("application/json")
                .content(
                    "{\"version\":\"0\",\"decision\":\"APPROVE\",\"reason\":\"確認\",\"dedupe_key\":\"k\"}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(reviews);
  }

  @Test
  @DisplayName("口コミの履歴は新しい順の Page で返すこと")
  @WithMockUser(authorities = "PERM_REVIEW_VIEW")
  void historyIsAPageNewestFirst() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    when(reviews.history(
            eq(STORE_ID),
            eq("user"),
            eq("00000000-0000-7000-8000-000000000101"),
            pageable.capture()))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/reviews/00000000-0000-7000-8000-000000000101/history")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, ReviewHistory::getId));
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
        .isEqualTo(
            PageRequest.of(
                1,
                20,
                Sort.by(Sort.Direction.ASC, ReviewRecord::getCreatedAt, ReviewRecord::getId)));

    mockMvc
        .perform(
            get("/reviews")
                .param("page", "99999999")
                .param("size", "100")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("公開口コミ一覧はページ番号と取得件数を Pageable として service へ渡すこと")
  @WithAnonymousUser
  void publicListBindsThePageRequest() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    when(reviews.listPublic(eq(STORE_ID), pageable.capture())).thenReturn(Page.empty());

    mockMvc
        .perform(get("/reviews/public?page=2&size=10").header("X-Store-ID", STORE_ID.toString()))
        .andExpect(status().isOk());

    assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
    assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
  }

  private static ReviewViews.Detail detail() {
    var row =
        ReviewRecord.receive(
            STORE_ID,
            new ReviewInput(
                "本文", null, ReceivedVia.PAPER, OffsetDateTime.parse("2026-01-01T00:00:00Z"), null),
            1L,
            "担当者",
            null,
            null);
    row.setId("00000000-0000-7000-8000-000000000101");
    row.setCreatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    ReflectionTestUtils.setField(row, "version", 0L);
    return new ReviewViews.Detail(
        new SpelAwareProxyProjectionFactory().createProjection(ReviewDetailsView.class, row),
        null,
        false);
  }
}
