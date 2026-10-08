package com.kizuna.order.api.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.order.api.dto.MonthlyRemunerationOrderSummary;
import com.kizuna.order.api.dto.SelfMonthlyRemunerationStoreSummary;
import com.kizuna.order.application.SelfMonthlyRemunerationService;
import com.kizuna.order.infrastructure.RemunerationQuery;
import com.kizuna.order.infrastructure.SelfMonthlyRemunerationQuery;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.store.application.StoreActivationService;
import com.kizuna.user.application.ActorIdentityService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;

@WebMvcTest(PlatformSelfMonthlyRemunerationController.class)
@Import({
  PlatformSelfMonthlyRemunerationControllerTest.MethodSecurityConfig.class,
  SelfMonthlyRemunerationService.class,
  StoreContext.class
})
@WithMockUser(username = "cast@example.test", roles = "CAST")
class PlatformSelfMonthlyRemunerationControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final String BASE = "/platform/me/monthly-remunerations";
  @Autowired private MockMvc mvc;
  @MockitoBean private ActorIdentityService actors;
  @MockitoBean private CastRepository people;
  @MockitoBean private SelfMonthlyRemunerationQuery selfQuery;
  @MockitoBean private RemunerationQuery monthlyQuery;
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreActivationService storeActivationService;
  @MockitoBean private PlatformTransactionManager transactionManager;

  private MockHttpServletRequestBuilder request(String path) {
    return get(path).principal(() -> "cast@example.test");
  }

  @BeforeEach
  void identity() {
    var person = Cast.builder().platformUserId(12L).build();
    person.setId(34L);
    when(actors.requireUserId("cast@example.test")).thenReturn(12L);
    when(people.findByPlatformUserId(12L)).thenReturn(Optional.of(person));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "/stores"})
  @WithMockUser(authorities = {"PERM_ORDER_MANAGE", "PERM_ORDER_SET_MANAGE"})
  @DisplayName("店舗・集合の受注権限は本人照会を許可しない")
  void staffCannotReadSelf(String suffix) throws Exception {
    mvc.perform(request(BASE + suffix).param("store_id", "1").param("month", "2026-09"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(actors, selfQuery, monthlyQuery);
  }

  @Test
  @DisplayName("追加された他人のIDを本人として扱わず専用応答を返す")
  void resolvesPrincipalAndProjectsOnlySelfFields() throws Exception {
    var month = YearMonth.of(2026, 9);
    when(selfQuery.storeName(34L, 1L)).thenReturn("退店店舗");
    when(monthlyQuery.total(1L, 34L, month)).thenReturn(7000L);
    when(monthlyQuery.orders(eq(1L), eq(34L), eq(month), any()))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    new MonthlyRemunerationOrderSummary(
                        "order", LocalDate.of(2026, 9, 30), "コース", 7000, false))));
    mvc.perform(
            request(BASE)
                .param("store_id", "1")
                .param("month", "2026-09")
                .param("person_id", "99")
                .param("cast_id", "other")
                .param("enrollment_id", "other"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.store_id").value(1))
        .andExpect(jsonPath("$.store_name").value("退店店舗"))
        .andExpect(jsonPath("$.total_remuneration").value(7000))
        .andExpect(jsonPath("$.orders.content[0].order_id").value("order"))
        .andExpect(jsonPath("$.orders.total_elements").value(1))
        .andExpect(jsonPath("$.person_id").doesNotExist())
        .andExpect(jsonPath("$.orders.content[0].customer_id").doesNotExist());
    verify(people).findByPlatformUserId(12L);
  }

  @Test
  @DisplayName("関係のない店舗は集計前に404を返す")
  void invisibleStore() throws Exception {
    when(selfQuery.storeName(34L, 9L)).thenThrow(new NotFoundException("報酬明細の店舗が見つかりません"));
    mvc.perform(request(BASE).param("store_id", "9").param("month", "2026-09"))
        .andExpect(status().isNotFound());
    verifyNoInteractions(monthlyQuery);
  }

  @Test
  @DisplayName("本人未作成なら店舗候補は空で月次対象は404になる")
  void unlinkedCast() throws Exception {
    when(people.findByPlatformUserId(12L)).thenReturn(Optional.empty());
    mvc.perform(request(BASE + "/stores"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
    mvc.perform(request(BASE).param("store_id", "1").param("month", "2026-09"))
        .andExpect(status().isNotFound());
    verifyNoInteractions(selfQuery, monthlyQuery);
  }

  @ParameterizedTest
  @CsvSource({"0,1", "-4,1", "3000,2000"})
  @DisplayName("店舗候補の取得件数は既存の上下限へ収める")
  void clampsStorePage(int size, int effective) throws Exception {
    var page = PageRequest.of(0, effective);
    when(selfQuery.stores(34L, page))
        .thenReturn(
            new PageImpl<>(List.of(new SelfMonthlyRemunerationStoreSummary(1L, "歴史店舗")), page, 1));
    mvc.perform(request(BASE + "/stores").param("size", String.valueOf(size)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(effective))
        .andExpect(jsonPath("$.content[0].store_name").value("歴史店舗"));
  }

  @ParameterizedTest
  @CsvSource({
    "0000-01,1,0",
    "2026-9,1,0",
    "2026-13,1,0",
    "2026-09,0,0",
    "2026-09,1,-1",
    "2026-09,1,2147483647"
  })
  @DisplayName("月・店舗・ページの不正値を400にする")
  void invalidCriteria(String month, String store, String page) throws Exception {
    mvc.perform(request(BASE).param("month", month).param("store_id", store).param("page", page))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").isString());
    verifyNoInteractions(selfQuery, monthlyQuery);
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "2147483647"})
  @DisplayName("候補ページも負値とoffset超過を拒否する")
  void invalidStorePage(String page) throws Exception {
    mvc.perform(request(BASE + "/stores").param("page", page)).andExpect(status().isBadRequest());
  }
}
