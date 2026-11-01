package com.kizuna.task.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.task.application.TaskLifecycle;
import com.kizuna.task.application.TaskOptions;
import com.kizuna.task.application.TaskQuery;
import com.kizuna.task.execution.TaskExecutor;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.application.StoreScopeResolver;
import com.kizuna.user.domain.PermissionCode;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 実行履歴が運用者の確認を経て、開始の新しい順を既定の並びとして Page で返すことの単体テスト。 */
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
  @MockitoBean private ServiceExecutionIdentityService identities;
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

    verify(identities).requireOperator(eq("user"), eq(PermissionCode.TASK_MANAGE));
    assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, "startedAt", "id"));
  }

  @Test
  @DisplayName("実行主体の候補と店舗の選択肢は名前順の Page で返し、範囲外のページを 400 にすること")
  @WithMockUser(authorities = "PERM_TASK_MANAGE")
  void optionListsArePagesInNameOrder() throws Exception {
    var candidates = ArgumentCaptor.forClass(Pageable.class);
    var stores = ArgumentCaptor.forClass(Pageable.class);
    when(options.candidates(eq("SERVICE_IDENTITY_CHECK"), eq(null), candidates.capture()))
        .thenReturn(new PageImpl<>(List.of()));
    when(options.stores(stores.capture())).thenReturn(new PageImpl<>(List.of()));
    var auth = SecurityContextHolder.getContext().getAuthentication();

    mockMvc
        .perform(get("/task-executions/service-identities").principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    mockMvc
        .perform(get("/task-executions/stores").principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    mockMvc
        .perform(
            get("/task-executions/stores")
                .param("page", "99999999")
                .param("size", "100")
                .principal(auth))
        .andExpect(status().isBadRequest());

    assertThat(candidates.getValue())
        .isEqualTo(PageRequest.of(0, 20, Sort.by("displayName", "id")));
    assertThat(stores.getValue()).isEqualTo(PageRequest.of(0, 20, Sort.by("name", "id")));
  }
}
