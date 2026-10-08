package com.kizuna.order.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.order.api.dto.MonthlyRemunerationOrderSummary;
import com.kizuna.order.api.platform.PlatformSelfDailyRemunerationController;
import com.kizuna.order.api.store.DailyRemunerationController;
import com.kizuna.order.application.DailyRemunerationService;
import com.kizuna.order.infrastructure.RemunerationQuery;
import com.kizuna.order.infrastructure.SelfMonthlyRemunerationQuery;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.store.application.StoreActivationService;
import com.kizuna.user.application.ActorIdentityService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;

@WebMvcTest({DailyRemunerationController.class, PlatformSelfDailyRemunerationController.class})
@Import({
  DailyRemunerationControllerTest.Security.class,
  DailyRemunerationService.class,
  StoreContext.class
})
@WithMockUser(
    username = "cast@example.test",
    authorities = {"PERM_ORDER_MANAGE", "ROLE_CAST"})
class DailyRemunerationControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class Security {}

  @Autowired private MockMvc mvc;
  @MockitoBean private RemunerationQuery query;
  @MockitoBean private SelfMonthlyRemunerationQuery selfQuery;
  @MockitoBean private ActorIdentityService actors;
  @MockitoBean private CastRepository people;
  @MockitoBean private SystemConfigService settings;
  @MockitoBean private StoreExistenceCheck storeExistence;
  @MockitoBean private StoreActivationService activation;
  @MockitoBean private PlatformTransactionManager transactions;

  @BeforeEach
  void fixture() {
    when(storeExistence.exists(anyLong())).thenReturn(true);
    var person = Cast.builder().platformUserId(12L).build();
    person.setId(34L);
    when(actors.requireUserId("cast@example.test")).thenReturn(12L);
    when(people.findByPlatformUserId(12L)).thenReturn(Optional.of(person));
    when(query.personName(1L, 34L)).thenReturn("本人");
    when(selfQuery.storeName(34L, 1L)).thenReturn("退店店舗");
    when(query.dailyTotal(eq(1L), eq(34L), any())).thenReturn(14000L);
    when(query.dailyOrders(eq(1L), eq(34L), any(), any()))
        .thenAnswer(
            call ->
                new PageImpl<>(
                    List.of(
                        new MonthlyRemunerationOrderSummary(
                            "order", call.getArgument(2), "保存済みコース", 7000, false)),
                    call.getArgument(3),
                    2));
  }

  private MockHttpServletRequestBuilder request(String scope, String date) {
    return get(scope.equals("store")
            ? "/store/daily-remunerations"
            : "/platform/me/daily-remunerations")
        .principal(() -> "cast@example.test")
        .header("X-Role", scope.equals("store") ? "store" : "platform")
        .header("X-Store-ID", "1")
        .param("person_id", "34")
        .param("store_id", "1")
        .param("business_date", date);
  }

  @ParameterizedTest
  @ValueSource(strings = {"store", "self"})
  void returnsFullTotalAndSafePagedRows(String scope) throws Exception {
    mvc.perform(request(scope, "2026-09-30").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.business_date").value("2026-09-30"))
        .andExpect(jsonPath("$.total_remuneration").value(14000))
        .andExpect(jsonPath("$.orders.total_elements").value(2))
        .andExpect(jsonPath("$.orders.content.length()").value(1))
        .andExpect(jsonPath("$.orders.content[0].service_summary").value("保存済みコース"))
        .andExpect(jsonPath("$.orders.content[0].customer_name").doesNotExist())
        .andExpect(jsonPath("$.orders.content[0].actor_id").doesNotExist())
        .andExpect(jsonPath("$.confirmation").doesNotExist());
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-02-30", "2026-2-01", "0000-01-01", "2026-13-01", "bad", ""})
  void invalidDayIs400OnBothSurfaces(String day) throws Exception {
    for (String scope : List.of("store", "self"))
      mvc.perform(request(scope, day))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").isString());
  }

  @ParameterizedTest
  @CsvSource({"-1,20", "2147483647,20"})
  void invalidPageIs400(String page, String size) throws Exception {
    for (String scope : List.of("store", "self"))
      mvc.perform(request(scope, "2026-09-30").param("page", page).param("size", size))
          .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1"})
  void invalidIdsAre400(String id) throws Exception {
    mvc.perform(
            get("/store/daily-remunerations")
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .param("person_id", id)
                .param("business_date", "2026-09-30"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/platform/me/daily-remunerations")
                .principal(() -> "cast@example.test")
                .param("store_id", id)
                .param("business_date", "2026-09-30"))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0001-01-01", "2024-02-29", "9999-12-31"})
  void validDateBounds(String day) throws Exception {
    mvc.perform(request("store", day))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.business_date").value(day));
  }

  @ParameterizedTest
  @CsvSource({"0,1", "-2,1", "2001,2000"})
  void clampsPageSize(int requested, int expected) throws Exception {
    mvc.perform(request("store", "2026-09-30").param("size", Integer.toString(requested)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orders.size").value(expected));
  }

  @Test
  void invisibleHistoryIs404() throws Exception {
    when(query.personName(1L, 34L)).thenThrow(new NotFoundException("キャスト本人が見つかりません"));
    when(selfQuery.storeName(34L, 1L)).thenThrow(new NotFoundException("報酬明細の店舗が見つかりません"));
    for (String scope : List.of("store", "self"))
      mvc.perform(request(scope, "2026-09-30")).andExpect(status().isNotFound());
  }

  @Test
  void castWithoutIdentityCannotReadHistory() throws Exception {
    when(people.findByPlatformUserId(12L)).thenReturn(Optional.empty());
    mvc.perform(request("self", "2026-09-30")).andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(authorities = "PERM_CAST_MANAGE")
  void unrelatedPermissionDoesNotGrantEitherRead() throws Exception {
    for (String scope : List.of("store", "self"))
      mvc.perform(request(scope, "2026-09-30")).andExpect(status().isForbidden());
  }
}
