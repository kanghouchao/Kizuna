package com.kizuna.order.api.platform;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.application.PlatformMonthlyRemunerationService;
import com.kizuna.order.infrastructure.MonthlyRemunerationQuery;
import com.kizuna.order.infrastructure.PlatformMonthlyRemunerationStores;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.store.application.StoreActivationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import java.time.YearMonth;
import java.util.List;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;

@WebMvcTest(PlatformMonthlyRemunerationController.class)
@Import({
  PlatformMonthlyRemunerationControllerTest.MethodSecurityConfig.class,
  PlatformMonthlyRemunerationService.class,
  PlatformMonthlyRemunerationStores.class,
  StoreContext.class
})
class PlatformMonthlyRemunerationControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  @EnableWebSecurity
  static class MethodSecurityConfig {
    @Bean
    SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
      return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }
  }

  @Autowired private MockMvc mvc;
  @MockitoBean private EntityManager entityManager;
  @MockitoBean private PlatformTransactionManager transactionManager;
  @MockitoBean private MonthlyRemunerationQuery query;
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreActivationService storeActivationService;
  private TypedQuery<String> names;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void data() {
    names = mock(TypedQuery.class, RETURNS_SELF);
    when(entityManager.createQuery(anyString(), eq(String.class))).thenReturn(names);
    when(names.getResultStream()).thenAnswer(ignored -> Stream.of("店舗名"));
  }

  private MockHttpServletRequestBuilder request(String suffix) {
    return get("/platform/monthly-remunerations" + suffix)
        .with(
            jwt()
                .jwt(
                    j ->
                        j.claim("storeScopeType", "SPECIFIC_STORES").claim("storeIds", List.of(1L)))
                .authorities(new SimpleGrantedAuthority("PERM_ORDER_SET_MANAGE")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "/casts"})
  @DisplayName("指定店舗が授権外なら本人候補と明細を403にする")
  void outsideScope(String suffix) throws Exception {
    mvc.perform(
            request(suffix)
                .param("store_id", "2")
                .param("person_id", "1")
                .param("month", "2026-09"))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "/casts", "/stores"})
  @DisplayName("単店権限では平台の月次照会を許可しない")
  void storePermissionIsInsufficient(String suffix) throws Exception {
    mvc.perform(
            request(suffix)
                .with(jwt().authorities(new SimpleGrantedAuthority("PERM_ORDER_MANAGE")))
                .param("store_id", "1")
                .param("person_id", "1")
                .param("month", "2026-09"))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @CsvSource({
    "2026-13,1,1,0",
    "0000-01,1,1,0",
    "2026-9,1,1,0",
    "2026-09,0,1,0",
    "2026-09,1,0,0",
    "2026-09,1,1,-1",
    "2026-09,1,1,2147483647"
  })
  @DisplayName("月・本人・店舗・ページの不正値は400になる")
  void invalidCriteria(String month, String person, String store, String page) throws Exception {
    mvc.perform(
            request("")
                .param("month", month)
                .param("person_id", person)
                .param("store_id", store)
                .param("page", page))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").isString());
  }

  @Test
  @DisplayName("授権作用域が解決不能なら候補一覧を拒否する")
  void unresolvedScope() throws Exception {
    mvc.perform(
            request("/stores")
                .with(jwt().authorities(new SimpleGrantedAuthority("PERM_ORDER_SET_MANAGE"))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("授権範囲内の消失店舗は404になる")
  void missingStore() throws Exception {
    when(names.getResultStream()).thenAnswer(ignored -> Stream.empty());
    mvc.perform(request("/casts").param("store_id", "1")).andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 1, 2001})
  @DisplayName("件数を許容範囲に収めても全月合計と店名を返す")
  void statement(int size) throws Exception {
    int limit = Math.clamp(size, 1, 2000);
    when(query.personName(1L, 9L)).thenReturn("最新源氏名");
    when(query.total(1L, 9L, YearMonth.of(2026, 9))).thenReturn(7000L);
    when(query.orders(1L, 9L, YearMonth.of(2026, 9), PageRequest.of(0, limit)))
        .thenReturn(Page.empty(PageRequest.of(0, limit)));
    mvc.perform(
            request("")
                .param("store_id", "1")
                .param("person_id", "9")
                .param("month", "2026-09")
                .param("size", String.valueOf(size)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.store_name").value("店舗名"))
        .andExpect(jsonPath("$.name").value("最新源氏名"))
        .andExpect(jsonPath("$.total_remuneration").value(7000))
        .andExpect(jsonPath("$.orders.size").value(limit));
  }

  @Test
  @DisplayName("本人候補がない店舗も空のページとして返す")
  void emptyCasts() throws Exception {
    when(query.casts(1L, null, PageRequest.of(0, 20))).thenReturn(Page.empty());
    mvc.perform(request("/casts").param("store_id", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @ParameterizedTest
  @CsvSource({"SPECIFIC_STORES,店舗", "ALL_STORES,店舗", "SPECIFIC_STORES,''", "ALL_STORES,''"})
  @DisplayName("店名検索と授権種別にかかわらず店名だけの候補ページを返す")
  @SuppressWarnings("unchecked")
  void stores(String scope, String search) throws Exception {
    TypedQuery<Tuple> rows = mock(TypedQuery.class, RETURNS_SELF);
    TypedQuery<Long> count = mock(TypedQuery.class, RETURNS_SELF);
    Tuple row = mock(Tuple.class);
    when(row.get(0, Long.class)).thenReturn(1L);
    when(row.get(1, String.class)).thenReturn("店舗名");
    when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(rows);
    when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(count);
    when(rows.getResultList()).thenReturn(List.of(row));
    when(count.getSingleResult()).thenReturn(1L);
    mvc.perform(
            request("/stores")
                .with(
                    jwt()
                        .jwt(j -> j.claim("storeScopeType", scope).claim("storeIds", List.of(1L)))
                        .authorities(new SimpleGrantedAuthority("PERM_ORDER_SET_MANAGE")))
                .param("search", search))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].store_id").value(1))
        .andExpect(jsonPath("$.content[0].store_name").value("店舗名"))
        .andExpect(jsonPath("$.total_elements").value(1));
  }
}
