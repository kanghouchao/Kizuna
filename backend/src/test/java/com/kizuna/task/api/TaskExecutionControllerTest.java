package com.kizuna.task.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.task.application.TaskLifecycle;
import com.kizuna.task.application.TaskOptions;
import com.kizuna.task.application.TaskQuery;
import com.kizuna.task.domain.ExecutionAttempt;
import com.kizuna.task.execution.ExecutionResult;
import com.kizuna.task.execution.TaskCommand;
import com.kizuna.task.execution.TaskExecutor;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.StoreScopeResolver;
import com.kizuna.user.domain.User;
import java.time.LocalDate;
import java.util.Arrays;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 実行履歴が開始の新しい順を既定の並びとして Page で返し、実行の記録に解決した実行者を渡すことの単体テスト。 */
@WebMvcTest(TaskExecutionController.class)
@Import({TaskExecutionControllerTest.MethodSecurityConfig.class, StoreScopeHolder.class})
class TaskExecutionControllerTest {

  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TaskExecutor executor;
  @MockitoBean private TaskOptions options;
  @MockitoBean private TaskLifecycle lifecycle;
  @MockitoBean private TaskQuery query;
  @MockitoBean private ActorIdentityService identities;
  @MockitoBean private AppProperties properties;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("実行履歴は開始の新しい順を既定の並びとして query を呼び、Page で返すこと")
  @WithMockUser(authorities = "PERM_TASK_MANAGE")
  void listIsAPageNewestFirst() throws Exception {
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(query.list(pageable.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

    mockMvc
        .perform(
            get("/task-executions")
                .principal(SecurityContextHolder.getContext().getAuthentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    assertThat(pageable.getValue().getSort())
        .isEqualTo(
            Sort.by(Sort.Direction.DESC, ExecutionAttempt::getStartedAt, ExecutionAttempt::getId));
  }

  @Test
  @DisplayName("どのサービス ID の店舗集合にも入っていない店舗を、呼び出し側の店舗集合で絞って id と名前で返すこと")
  void uncoveredStoresAreListedWithinTheCallerScope() throws Exception {
    var scope = new StoreScope(Set.of(3L));
    when(storeScopeResolver.resolve(any())).thenReturn(scope);
    when(options.uncoveredStores(scope))
        .thenReturn(List.of(new TaskOptions.UncoveredStore(3L, "新店")));

    mockMvc
        .perform(get("/task-executions/uncovered-stores").with(jwt("PERM_TASK_MANAGE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(3))
        .andExpect(jsonPath("$[0].name").value("新店"));
  }

  @Test
  @DisplayName("店舗集合を解決できなければ未登録の店舗を返さず 403 にすること")
  void uncoveredStoresWithoutAScopeAreForbidden() throws Exception {
    mockMvc
        .perform(get("/task-executions/uncovered-stores").with(jwt("PERM_TASK_MANAGE")))
        .andExpect(status().isForbidden());

    verify(options, never()).uncoveredStores(any());
  }

  @Test
  @DisplayName("どの端点も、処理の管理権限を欠く要求を 403 で拒み、何も読まず実行もしないこと")
  void everyEndpointRequiresTaskManage() throws Exception {
    when(storeScopeResolver.resolve(any())).thenReturn(new StoreScope(Set.of(3L)));
    var other = jwt("PERM_TASK_EXECUTE", "PERM_AUDIT_VIEW");
    String reason = "{\"reason\":\"確認\"}";
    String execution =
        "{\"task_name\":\"NOTIFICATION_DELIVER\",\"logical_key\":\"k\",\"service_user_id\":10,"
            + "\"period_start\":\"2026-10-07\",\"period_end\":\"2026-10-07\"}";
    for (var request :
        List.of(
            get("/task-executions"),
            get("/task-executions/service-identities"),
            get("/task-executions/task-types"),
            get("/task-executions/uncovered-stores"),
            get("/task-executions/1"),
            post("/task-executions").contentType(MediaType.APPLICATION_JSON).content(execution),
            post("/task-executions/1/retries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reason),
            post("/task-executions/1/interruption")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reason)))
      mockMvc.perform(request.with(other)).andExpect(status().isForbidden());

    verifyNoInteractions(executor, options, lifecycle, query, identities);
  }

  private static RequestPostProcessor jwt(String... permissions) {
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
  @DisplayName("実行主体の候補は名前順の Page で返し、範囲外のページを 400 にすること")
  @WithMockUser(authorities = "PERM_TASK_MANAGE")
  void candidatesArePagesInNameOrder() throws Exception {
    var candidates = ArgumentCaptor.forClass(Pageable.class);
    when(options.candidates(eq("SERVICE_IDENTITY_CHECK"), candidates.capture()))
        .thenReturn(new PageImpl<>(List.of()));
    var auth = SecurityContextHolder.getContext().getAuthentication();

    mockMvc
        .perform(get("/task-executions/service-identities").principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    mockMvc
        .perform(
            get("/task-executions/service-identities")
                .param("page", "99999999")
                .param("size", "100")
                .principal(auth))
        .andExpect(status().isBadRequest());

    assertThat(candidates.getValue())
        .isEqualTo(PageRequest.of(0, 20, Sort.by(User::getDisplayName, User::getId)));
  }

  @Test
  @DisplayName("実行要求を処理条件のとおりに渡し、店舗の指定を受け付けないこと")
  @WithMockUser(authorities = "PERM_TASK_MANAGE")
  void executeBindsTheCommandAndRejectsAStore() throws Exception {
    var command = ArgumentCaptor.forClass(TaskCommand.class);
    var auth = SecurityContextHolder.getContext().getAuthentication();
    String body =
        "{\"task_name\":\"NOTIFICATION_DELIVER\",\"logical_key\":\"k\",\"service_user_id\":10,"
            + "\"period_start\":\"2026-10-07\",\"period_end\":\"2026-10-07\"%s}";

    var day = LocalDate.of(2026, 10, 7);
    var execution =
        new ExecutionResult(
            1L,
            2L,
            1,
            null,
            "NOTIFICATION_DELIVER",
            "k",
            10L,
            "通知主体",
            day,
            day,
            "MANUAL",
            null,
            null,
            "SUCCEEDED",
            null,
            null,
            null,
            null);
    var actor = new AuditActor(9L, "STAFF", "運用担当");
    when(identities.requireActor("user")).thenReturn(actor);
    when(executor.execute(any(), eq(actor)))
        .thenReturn(new TaskExecutor.Submission(execution, true));

    mockMvc
        .perform(
            post("/task-executions")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted("")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.execution.id").value(1))
        .andExpect(jsonPath("$.execution.task_name").value("NOTIFICATION_DELIVER"))
        .andExpect(jsonPath("$.execution.store_id").doesNotExist());
    verify(executor).execute(command.capture(), eq(actor));
    assertThat(command.getValue())
        .isEqualTo(new TaskCommand("NOTIFICATION_DELIVER", "k", 10L, day, day));

    mockMvc
        .perform(
            post("/task-executions")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(",\"store_id\":\"1\"")))
        .andExpect(status().isBadRequest());
  }
}
