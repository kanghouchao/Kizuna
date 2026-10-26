package com.kizuna.review.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.review.application.ReviewService;
import com.kizuna.review.domain.ReviewChange;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
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

  // MaintenanceModeInterceptor（HandlerInterceptor）と CurrentStoreArgumentResolver（引数解決器）は
  // @WebMvcTest に自動で取り込まれるため、その依存もモックで満たす必要がある。
  @MockitoBean private SystemConfigService systemConfigService;
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
                .with(csrf())
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser())
                .contentType("application/json")
                .content(
                    "{\"version\":0,\"decision\":\"APPROVE\",\"reason\":\"確認\",\"dedupe_key\":\"k\"}"))
        .andExpect(status().isOk());

    verify(reviews).change(eq(STORE_ID), eq("user"), any(ReviewChange.class));
  }
}
