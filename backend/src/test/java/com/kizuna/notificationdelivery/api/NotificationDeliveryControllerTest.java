package com.kizuna.notificationdelivery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.notificationdelivery.application.NotificationService;
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
@WebMvcTest(NotificationDeliveryController.class)
@Import({NotificationDeliveryControllerTest.MethodSecurityConfig.class, StoreScopeHolder.class})
class NotificationDeliveryControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final Long STORE_ID = 7L;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private NotificationService notifications;

  // CurrentStoreArgumentResolver（引数解決器）は @WebMvcTest に自動で取り込まれるため、
  // その依存もモックで満たす必要がある。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  /** handler は Authentication を要求の principal から受けるため、模擬利用者を principal にも載せる。 */
  private static Authentication currentUser() {
    return SecurityContextHolder.getContext().getAuthentication();
  }

  @Test
  @DisplayName("通知詳細は名乗られた店舗の id で service を呼ぶこと")
  @WithMockUser(authorities = "PERM_NOTIFICATION_VIEW")
  void detailPassesTheCurrentStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            get("/notification-deliveries/101")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isOk());

    verify(notifications).get(STORE_ID, "user", "101");
  }

  @Test
  @DisplayName("送信予約は名乗られた店舗の id で service を呼ぶこと")
  @WithMockUser(authorities = {"PERM_NOTIFICATION_VIEW", "PERM_NOTIFICATION_SEND"})
  void queuePassesTheCurrentStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            post("/notification-deliveries/101/queue")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser())
                .contentType("application/json")
                .content("{\"version\":0,\"reason\":\"確認済み\"}"))
        .andExpect(status().isOk());

    verify(notifications).queue(STORE_ID, "user", "101", 0L, "確認済み", false);
  }

  @Test
  @DisplayName("通知一覧は新しい順を既定の並びとして名乗られた店舗で service を呼び、Page で返すこと")
  @WithMockUser(authorities = "PERM_NOTIFICATION_VIEW")
  void listIsAPageNewestFirst() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(notifications.list(eq(STORE_ID), eq("user"), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

    mockMvc
        .perform(
            get("/notification-deliveries")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
  }

  @Test
  @DisplayName("送信履歴は新しい順を既定の並びとして名乗られた店舗で service を呼び、Page で返すこと")
  @WithMockUser(authorities = "PERM_NOTIFICATION_VIEW")
  void historyIsAPageNewestFirst() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(notifications.history(eq(STORE_ID), eq("user"), eq("101"), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

    mockMvc
        .perform(
            get("/notification-deliveries/101/attempts")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
  }
}
