package com.kizuna.order.api.store;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.application.MonthlyRemunerationService;
import com.kizuna.order.infrastructure.MonthlyRemunerationQuery;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.store.application.StoreActivationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;

@WebMvcTest(MonthlyRemunerationController.class)
@Import({
  MonthlyRemunerationControllerTest.MethodSecurityConfig.class,
  MonthlyRemunerationService.class,
  MonthlyRemunerationQuery.class,
  StoreContext.class
})
@WithMockUser(authorities = "PERM_ORDER_MANAGE")
class MonthlyRemunerationControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mvc;
  @MockitoBean private EntityManager entityManager;
  @MockitoBean private PlatformTransactionManager transactionManager;
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreActivationService storeActivationService;

  @BeforeEach
  void storeExists() {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
  }

  private MockHttpServletRequestBuilder request(String suffix) {
    return get("/store/monthly-remunerations" + suffix)
        .header("X-Role", "store")
        .header("X-Store-ID", "1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "/casts"})
  @DisplayName("本人管理権限だけでは月次報酬を照会できない")
  @WithMockUser(authorities = "PERM_CAST_MANAGE")
  void castManagementDoesNotGrantRemuneration(String suffix) throws Exception {
    mvc.perform(request(suffix).param("person_id", "1").param("month", "2026-09"))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @CsvSource({
    "2026-13,1,0",
    "2026-9,1,0",
    "0000-01,1,0",
    "2026-09,0,0",
    "2026-09,1,-1",
    "2026-09,1,2147483647"
  })
  @DisplayName("店舗受注権限でも不正な本人・対象月・ページは入力エラーになる")
  void rejectsInvalidCriteria(String month, String person, String page) throws Exception {
    mvc.perform(request("").param("person_id", person).param("month", month).param("page", page))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").isString());
  }

  @Test
  @DisplayName("本人管理権限を要求せず店舗から不可視の本人を404にする")
  @SuppressWarnings("unchecked")
  void invisiblePersonIsNotFoundWithOnlyOrderPermission() throws Exception {
    // 永続化境界だけを空の照会結果に置き換え、HTTP・サービス・応答への例外変換を通す。
    TypedQuery<String> name = mock(TypedQuery.class, RETURNS_SELF);
    when(entityManager.createQuery(anyString(), eq(String.class))).thenReturn(name);
    when(name.getResultStream()).thenReturn(Stream.empty());
    mvc.perform(request("").param("person_id", "1").param("month", "2026-09"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("キャスト本人が見つかりません"));
  }

  @Test
  @DisplayName("本人候補の不正なページ指定も400にする")
  void invalidCandidatePage() throws Exception {
    mvc.perform(request("/casts").param("page", "-1")).andExpect(status().isBadRequest());
  }
}
