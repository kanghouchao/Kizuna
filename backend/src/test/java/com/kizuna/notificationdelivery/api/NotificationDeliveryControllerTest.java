package com.kizuna.notificationdelivery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.notificationdelivery.application.ContactDecision;
import com.kizuna.notificationdelivery.application.DeliveryView;
import com.kizuna.notificationdelivery.application.NotificationService;
import com.kizuna.notificationdelivery.domain.DeliveryAttempt;
import com.kizuna.notificationdelivery.domain.DeliveryChannel;
import com.kizuna.notificationdelivery.domain.DeliveryContent;
import com.kizuna.notificationdelivery.domain.DeliveryPurpose;
import com.kizuna.notificationdelivery.domain.DeliveryResolution;
import com.kizuna.notificationdelivery.domain.DeliverySourceType;
import com.kizuna.notificationdelivery.domain.DeliveryStatus;
import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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
    when(notifications.get(STORE_ID, "user", "101")).thenReturn(view());

    mockMvc
        .perform(
            get("/notification-deliveries/101")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("101"))
        .andExpect(jsonPath("$.contact_decision").value("ALLOWED"))
        .andExpect(jsonPath("$.transport_availability").value("AVAILABLE"));

    verify(notifications).get(STORE_ID, "user", "101");
  }

  @Test
  @DisplayName("送信予約は名乗られた店舗の id で service を呼ぶこと")
  @WithMockUser(authorities = {"PERM_NOTIFICATION_VIEW", "PERM_NOTIFICATION_SEND"})
  void queuePassesTheCurrentStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(notifications.queue(STORE_ID, "user", "101", 0L, "確認済み", false)).thenReturn(view());

    mockMvc
        .perform(
            post("/notification-deliveries/101/queue")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser())
                .contentType("application/json")
                .content("{\"version\":0,\"reason\":\"確認済み\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("101"))
        .andExpect(jsonPath("$.contact_decision").value("ALLOWED"))
        .andExpect(jsonPath("$.transport_availability").value("AVAILABLE"));

    verify(notifications).queue(STORE_ID, "user", "101", 0L, "確認済み", false);
  }

  @Test
  @DisplayName("同じ重複排除キーの同時作成に敗れた要求は、既存の下書きの読み直しとして 200 を返すこと")
  @WithMockUser(authorities = {"PERM_NOTIFICATION_VIEW", "PERM_NOTIFICATION_MANAGE"})
  void losingTheDedupeKeyRaceReplaysTheExistingDraft() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(notifications.create(eq(STORE_ID), eq("user"), any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "duplicate",
                new ConstraintViolationException(
                    "could not execute statement",
                    new SQLException("duplicate key value", "23505"),
                    "uq_t_notification_deliveries_key")));
    when(notifications.replay(eq(STORE_ID), eq("user"), any()))
        .thenReturn(new NotificationService.Created(view(), false));

    mockMvc
        .perform(
            post("/notification-deliveries")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser())
                .contentType("application/json")
                .content(
                    """
                    {"source_type":"ORDER","source_id":"1","channel":"EMAIL","purpose":"BUSINESS",
                     "subject":"件名","body":"本文","scheduled_at":"2026-10-07T00:00:00Z",
                     "dedupe_key":"race"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("101"))
        .andExpect(jsonPath("$.contact_decision").value("ALLOWED"))
        .andExpect(jsonPath("$.transport_availability").value("AVAILABLE"));

    verify(notifications).replay(eq(STORE_ID), eq("user"), any());
  }

  @Test
  @DisplayName("作成口は販促の通知を 400 で拒み、service を呼ばないこと")
  @WithMockUser(authorities = {"PERM_NOTIFICATION_VIEW", "PERM_NOTIFICATION_MANAGE"})
  void creationRejectsMarketing() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            post("/notification-deliveries")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser())
                .contentType("application/json")
                .content(
                    """
                    {"source_type":"ORDER","source_id":"1","channel":"EMAIL","purpose":"MARKETING",
                     "subject":"件名","body":"本文","scheduled_at":"2026-10-07T00:00:00Z",
                     "dedupe_key":"promo"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("作成できる通知は業務連絡だけです"));

    verify(notifications, never()).create(any(), any(), any());
  }

  @Test
  @DisplayName("作成口は未定義のチャネルを 400 で拒むこと")
  @WithMockUser(authorities = {"PERM_NOTIFICATION_VIEW", "PERM_NOTIFICATION_MANAGE"})
  void creationRejectsUnknownChannel() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            post("/notification-deliveries")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser())
                .contentType("application/json")
                .content(
                    """
                    {"source_type":"ORDER","source_id":"1","channel":"SMS","purpose":"BUSINESS",
                     "subject":"件名","body":"本文","scheduled_at":"2026-10-07T00:00:00Z",
                     "dedupe_key":"sms"}
                    """))
        .andExpect(status().isBadRequest());

    verify(notifications, never()).create(any(), any(), any());
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
    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, NotificationDelivery::getId));
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

    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, DeliveryAttempt::getId));
  }

  @Test
  @DisplayName("結果不明の判定は名乗られた店舗・版・判定・理由で service を呼び、201 で通知を返すこと")
  @WithMockUser(authorities = {"PERM_NOTIFICATION_VIEW", "PERM_NOTIFICATION_SEND"})
  void resolutionPassesTheCurrentStoreAndOutcome() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(notifications.resolve(STORE_ID, "user", "101", 3L, DeliveryResolution.FAILED, "未配信を確認"))
        .thenReturn(view());

    mockMvc
        .perform(
            post("/notification-deliveries/101/resolutions")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser())
                .contentType("application/json")
                .content("{\"version\":3,\"outcome\":\"FAILED\",\"reason\":\"未配信を確認\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("101"))
        .andExpect(jsonPath("$.attempt_count").value(0));

    verify(notifications).resolve(STORE_ID, "user", "101", 3L, DeliveryResolution.FAILED, "未配信を確認");
  }

  @Test
  @DisplayName("送信権限の無い職員の判定は 403 で、service を呼ばないこと")
  @WithMockUser(authorities = {"PERM_NOTIFICATION_VIEW", "PERM_NOTIFICATION_MANAGE"})
  void resolutionRequiresSendPermission() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            post("/notification-deliveries/101/resolutions")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser())
                .contentType("application/json")
                .content("{\"version\":3,\"outcome\":\"SENT\",\"reason\":\"確認\"}"))
        .andExpect(status().isForbidden());

    verify(notifications, never()).resolve(any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("判定は送達済み・未送達の二つだけで、結果不明や空の理由は 400 で拒むこと")
  @WithMockUser(authorities = {"PERM_NOTIFICATION_VIEW", "PERM_NOTIFICATION_SEND"})
  void resolutionRejectsOtherOutcomesAndBlankReason() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    for (var body :
        List.of(
            "{\"version\":3,\"outcome\":\"UNKNOWN\",\"reason\":\"確認\"}",
            "{\"version\":3,\"outcome\":\"BLOCKED\",\"reason\":\"確認\"}",
            "{\"version\":3,\"reason\":\"確認\"}",
            "{\"version\":3,\"outcome\":\"SENT\",\"reason\":\"  \"}",
            "{\"outcome\":\"SENT\",\"reason\":\"確認\"}"))
      mockMvc
          .perform(
              post("/notification-deliveries/101/resolutions")
                  .header("X-Store-ID", STORE_ID.toString())
                  .principal(currentUser())
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isBadRequest());

    verify(notifications, never()).resolve(any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("職員の判定の試行は実行 ID を持たず、失敗コード MANUAL_RESOLUTION で返ること")
  @WithMockUser(authorities = "PERM_NOTIFICATION_VIEW")
  void historyShowsManualResolutionWithoutExecution() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    var row = view().row();
    ReflectionTestUtils.setField(row, "status", DeliveryStatus.UNKNOWN);
    row.resolve(null, DeliveryResolution.FAILED, "未配信を確認");
    var attempt =
        DeliveryAttempt.resolution(
            row, 9L, "通知担当", OffsetDateTime.parse("2026-10-07T09:00:00+09:00"));
    when(notifications.history(eq(STORE_ID), eq("user"), eq("101"), any()))
        .thenReturn(new PageImpl<>(List.of(attempt), PageRequest.of(0, 50), 1));

    mockMvc
        .perform(
            get("/notification-deliveries/101/attempts")
                .header("X-Store-ID", STORE_ID.toString())
                .principal(currentUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].status").value("FAILED"))
        .andExpect(jsonPath("$.content[0].failure_code").value("MANUAL_RESOLUTION"))
        .andExpect(jsonPath("$.content[0].task_execution_id").doesNotExist())
        .andExpect(jsonPath("$.content[0].reason").value("未配信を確認"))
        .andExpect(jsonPath("$.content[0].actor_name").doesNotExist());
  }

  /** 店舗集合の解決は JwtAuthenticationToken の主体にだけ掛かるので、Bearer の職員を文脈と principal の両方に載せる。 */
  private static RequestPostProcessor staff(String... permissions) {
    var token =
        new JwtAuthenticationToken(
            Jwt.withTokenValue("staff").header("alg", "none").subject("user").build(),
            Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList(),
            "user");
    SecurityContextHolder.getContext().setAuthentication(token);
    return request -> {
      request.setUserPrincipal(token);
      return request;
    };
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("店舗を名乗らない一覧は、全店舗を授権された職員ならグループ級（null）で service を呼ぶこと")
  void groupListPassesNoStoreForAllStoreStaff() throws Exception {
    when(storeScopeResolver.resolve(any())).thenReturn(new StoreScope(true, Set.of()));
    when(notifications.list(isNull(), eq("user"), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

    mockMvc
        .perform(get("/notification-deliveries").with(staff("PERM_NOTIFICATION_VIEW")))
        .andExpect(status().isOk());

    verify(notifications).list(isNull(), eq("user"), any());
  }

  @Test
  @DisplayName("全店舗を授権された職員はグループ級の通知へ送信予約できること")
  void groupQueueReachesServiceForAllStoreStaff() throws Exception {
    when(storeScopeResolver.resolve(any())).thenReturn(new StoreScope(true, Set.of()));
    when(notifications.queue(null, "user", "101", 0L, "確認済み", false)).thenReturn(view());

    mockMvc
        .perform(
            post("/notification-deliveries/101/queue")
                .with(staff("PERM_NOTIFICATION_VIEW", "PERM_NOTIFICATION_SEND"))
                .contentType("application/json")
                .content("{\"version\":0,\"reason\":\"確認済み\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("全店舗を授権されていない職員は、どの端点でもグループ級の通知に到達できないこと")
  void groupRowsAreForbiddenWithoutAllStores() throws Exception {
    for (StoreScope scope : Arrays.asList(new StoreScope(false, Set.of(STORE_ID)), null)) {
      when(storeScopeResolver.resolve(any())).thenReturn(scope);
      var all =
          staff("PERM_NOTIFICATION_VIEW", "PERM_NOTIFICATION_MANAGE", "PERM_NOTIFICATION_SEND");
      var action = "{\"version\":0,\"reason\":\"確認済み\"}";
      for (var request :
          List.of(
              get("/notification-deliveries").with(all),
              get("/notification-deliveries/101").with(all),
              get("/notification-deliveries/101/attempts").with(all),
              post("/notification-deliveries/101/queue")
                  .with(all)
                  .contentType("application/json")
                  .content(action),
              post("/notification-deliveries/101/retries")
                  .with(all)
                  .contentType("application/json")
                  .content(action),
              post("/notification-deliveries/101/resolutions")
                  .with(all)
                  .contentType("application/json")
                  .content("{\"version\":0,\"outcome\":\"SENT\",\"reason\":\"確認\"}"),
              post("/notification-deliveries")
                  .with(all)
                  .contentType("application/json")
                  .content(
                      "{\"source_type\":\"MEMBER\",\"source_id\":\"1\",\"channel\":\"EMAIL\","
                          + "\"purpose\":\"BUSINESS\",\"subject\":\"件名\",\"body\":\"本文\","
                          + "\"scheduled_at\":\"2026-10-07T09:00:00+09:00\",\"dedupe_key\":\"k\"}")))
        mockMvc.perform(request).andExpect(status().isForbidden());
    }

    verifyNoInteractions(notifications);
  }

  private static DeliveryView view() {
    var row =
        NotificationDelivery.draft(
            STORE_ID,
            new DeliveryContent(
                DeliverySourceType.ORDER,
                "1",
                DeliveryChannel.EMAIL,
                DeliveryPurpose.BUSINESS,
                "件名",
                "本文",
                OffsetDateTime.parse("2026-10-07T09:00:00+09:00"),
                "race"));
    ReflectionTestUtils.setField(row, "id", "101");
    return new DeliveryView(row, ContactDecision.ALLOWED, true);
  }
}
