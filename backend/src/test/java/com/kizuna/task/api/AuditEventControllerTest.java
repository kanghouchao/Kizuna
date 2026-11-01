package com.kizuna.task.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.audit.recording.AuditReader;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
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

/** 監査一覧が運用者の確認を経て、記録の新しい順を既定の並びとして Page で返すことの単体テスト。 */
@WebMvcTest(AuditEventController.class)
@Import({AuditEventControllerTest.MethodSecurityConfig.class, StoreScopeHolder.class})
class AuditEventControllerTest {

  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuditReader reader;
  @MockitoBean private ServiceExecutionIdentityService identities;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("監査一覧は記録の新しい順を既定の並びとして reader を呼び、Page で返すこと")
  @WithMockUser(authorities = "PERM_AUDIT_VIEW")
  void listIsAPageNewestFirst() throws Exception {
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(reader.list(isNull(), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

    mockMvc
        .perform(
            get("/audit-events").principal(SecurityContextHolder.getContext().getAuthentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    verify(identities).requireOperator(eq("user"), eq(PermissionCode.AUDIT_VIEW));
    assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
  }
}
