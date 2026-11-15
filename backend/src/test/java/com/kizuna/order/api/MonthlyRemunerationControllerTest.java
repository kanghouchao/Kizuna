package com.kizuna.order.api;

import static com.kizuna.shared.web.JwtRequests.jwtUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.api.dto.OrderMapperImpl;
import com.kizuna.order.application.MonthlyRemunerationService;
import com.kizuna.order.infrastructure.RemunerationQuery;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import java.util.Set;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;

@WebMvcTest(MonthlyRemunerationController.class)
@Import({
  MonthlyRemunerationControllerTest.MethodSecurityConfig.class,
  MonthlyRemunerationService.class,
  OrderMapperImpl.class,
  RemunerationQuery.class,
  StoreScopeHolder.class
})
class MonthlyRemunerationControllerTest {

  private static final long USER_ID = 42L;

  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mvc;
  @MockitoBean private EntityManager entityManager;
  @MockitoBean private PlatformTransactionManager transactionManager;
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @BeforeEach
  void storeScope() {
    when(storeScopeResolver.resolve(any())).thenReturn(new StoreScope(Set.of(1L, 7L)));
  }

  @BeforeEach
  void storeExists() {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
  }

  private MockHttpServletRequestBuilder request(String suffix) {
    return get("/monthly-remunerations" + suffix).header("X-Store-ID", "1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "/casts"})
  @DisplayName("本人管理権限だけでは月次報酬を照会できない")
  void castManagementDoesNotGrantRemuneration(String suffix) throws Exception {
    mvc.perform(
            request(suffix)
                .param("person_id", "1")
                .param("month", "2026-09")
                .with(jwtUser(USER_ID, "PERM_CAST_MANAGE")))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @CsvSource({"2026-13,1,0", "2026-9,1,0", "0000-01,1,0", "2026-09,0,0", "2026-09,1,2147483647"})
  @DisplayName("店舗受注権限でも不正な本人・対象月・ページは入力エラーになる")
  void rejectsInvalidCriteria(String month, String person, String page) throws Exception {
    mvc.perform(
            request("")
                .param("person_id", person)
                .param("month", month)
                .param("page", page)
                .with(jwtUser(USER_ID, "PERM_ORDER_MANAGE")))
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
    mvc.perform(
            request("")
                .param("person_id", "1")
                .param("month", "2026-09")
                .with(jwtUser(USER_ID, "PERM_ORDER_MANAGE")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("キャスト本人が見つかりません"));
  }

  @Test
  @DisplayName("店舗集合を跨ぐ照会の権限では現在店舗の月次報酬にも本人候補にも届かない")
  void crossStoreViewerDoesNotReachStoreReads() throws Exception {
    mvc.perform(
            request("")
                .param("person_id", "1")
                .param("month", "2026-09")
                .with(jwtUser(USER_ID, "PERM_ORDER_SET_VIEW")))
        .andExpect(status().isForbidden());
    mvc.perform(request("/casts").with(jwtUser(USER_ID, "PERM_ORDER_SET_VIEW")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(entityManager);
  }

  @Test
  @DisplayName("現在店舗の本人候補の範囲外のページ指定は既定へ正規化される")
  @SuppressWarnings("unchecked")
  void candidatesNormalizeOutOfRangePaging() throws Exception {
    TypedQuery<Tuple> rows = mock(TypedQuery.class, RETURNS_SELF);
    TypedQuery<Long> count = mock(TypedQuery.class, RETURNS_SELF);
    when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(rows);
    when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(count);
    when(count.getSingleResult()).thenReturn(0L);
    mvc.perform(
            request("/casts")
                .param("page", "-1")
                .param("size", "0")
                .with(jwtUser(USER_ID, "PERM_ORDER_MANAGE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.number").value(0))
        .andExpect(jsonPath("$.size").value(20));
    verify(rows).setFirstResult(0);
    verify(rows).setMaxResults(20);
  }

  @Test
  @DisplayName("本人候補の取得位置が int を超えるページ指定は400にする")
  void candidateOffsetBeyondIntIs400() throws Exception {
    mvc.perform(
            request("/casts")
                .param("page", "99999999")
                .param("size", "100")
                .with(jwtUser(USER_ID, "PERM_ORDER_MANAGE")))
        .andExpect(status().isBadRequest());
  }
}
