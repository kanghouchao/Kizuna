package com.kizuna.task.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.recording.AuditEventResponse;
import com.kizuna.audit.recording.AuditEventSummary;
import com.kizuna.audit.recording.AuditReader;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 監査の照会が閲覧権限と呼び出し者の店舗集合だけで判定され、集合を reader へ渡すことの単体テスト。 */
@WebMvcTest(AuditEventController.class)
@Import(AuditEventControllerTest.MethodSecurityConfig.class)
class AuditEventControllerTest {

  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final StoreScope STORE_ONE = new StoreScope(false, Set.of(1L));

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuditReader reader;
  @MockitoBean private StoreScopeHolder storeScopes;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("一覧は呼び出し者の店舗集合と記録の新しい順の既定の並びで reader を呼び、Page で返すこと")
  @WithMockUser(authorities = "PERM_AUDIT_VIEW")
  void listPassesTheCallersStoreSetNewestFirst() throws Exception {
    when(storeScopes.current()).thenReturn(STORE_ONE);
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(reader.list(eq(STORE_ONE), isNull(), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

    mockMvc
        .perform(get("/audit-events"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, AuditEvent::getOccurredAt, AuditEvent::getId));
  }

  @Test
  @DisplayName("単件は呼び出し者の店舗集合で reader を呼ぶこと")
  @WithMockUser(authorities = "PERM_AUDIT_VIEW")
  void getPassesTheCallersStoreSet() throws Exception {
    when(storeScopes.current()).thenReturn(STORE_ONE);
    var summary =
        new AuditEventSummary(
            7L,
            OffsetDateTime.now(),
            1L,
            "STAFF",
            "監査者",
            null,
            "X",
            "SUCCEEDED",
            "T",
            "1",
            null,
            null);
    when(reader.get(STORE_ONE, 7L)).thenReturn(new AuditEventResponse(summary, Map.of(), Map.of()));

    mockMvc
        .perform(get("/audit-events/7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event.id").value(7));
  }

  @Test
  @DisplayName("店舗集合を解決できない呼び出しは 403 で、reader を呼ばないこと")
  @WithMockUser(authorities = "PERM_AUDIT_VIEW")
  void unresolvedStoreSetIsForbidden() throws Exception {
    when(storeScopes.current()).thenReturn(null);

    mockMvc.perform(get("/audit-events")).andExpect(status().isForbidden());
    mockMvc.perform(get("/audit-events/7")).andExpect(status().isForbidden());

    verifyNoInteractions(reader);
  }

  @Test
  @DisplayName("閲覧権限の無い者は一覧も単件も 403 で、reader を呼ばないこと")
  @WithMockUser(authorities = "PERM_TASK_MANAGE")
  void callerWithoutAuditViewIsForbidden() throws Exception {
    when(storeScopes.current()).thenReturn(STORE_ONE);

    mockMvc.perform(get("/audit-events")).andExpect(status().isForbidden());
    mockMvc.perform(get("/audit-events/7")).andExpect(status().isForbidden());

    verifyNoInteractions(reader);
  }
}
