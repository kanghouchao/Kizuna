package com.kizuna.order.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.api.dto.OrderMapperImpl;
import com.kizuna.order.application.DailyRemunerationService;
import com.kizuna.order.domain.RemunerationOrderRow;
import com.kizuna.order.infrastructure.RemunerationQuery;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.util.List;
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

@WebMvcTest(DailyRemunerationController.class)
@Import({
  DailyRemunerationControllerTest.Security.class,
  DailyRemunerationService.class,
  OrderMapperImpl.class,
  StoreScopeHolder.class
})
@WithMockUser(authorities = "PERM_ORDER_MANAGE")
class DailyRemunerationControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class Security {}

  @Autowired private MockMvc mvc;
  @MockitoBean private RemunerationQuery query;
  @MockitoBean private SystemConfigService settings;
  @MockitoBean private StoreExistenceCheck storeExistence;
  @MockitoBean StoreScopeResolver storeScopeResolver;
  @MockitoBean private PlatformTransactionManager transactions;

  @BeforeEach
  void fixture() {
    when(storeExistence.exists(anyLong())).thenReturn(true);
    when(query.personName(1L, 34L)).thenReturn("本人");
    when(query.dailyTotal(eq(1L), eq(34L), any())).thenReturn(14000L);
    when(query.dailyOrders(eq(1L), eq(34L), any(), any()))
        .thenAnswer(
            call ->
                new PageImpl<>(
                    List.of(
                        new RemunerationOrderRow(
                            "order", call.getArgument(2), "保存済みコース", 7000, false)),
                    call.getArgument(3),
                    2));
  }

  private MockHttpServletRequestBuilder request(String date) {
    return get("/daily-remunerations")
        .header("X-Store-ID", "1")
        .param("person_id", "34")
        .param("business_date", date);
  }

  @Test
  void returnsFullTotalAndSafePagedRows() throws Exception {
    mvc.perform(request("2026-09-30").param("size", "1"))
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

  @Test
  void storeReadUsesTheNamedStore() throws Exception {
    when(query.personName(7L, 34L)).thenReturn("本人");
    when(query.dailyTotal(eq(7L), eq(34L), any())).thenReturn(7000L);
    when(query.dailyOrders(eq(7L), eq(34L), any(), any()))
        .thenAnswer(call -> new PageImpl<>(List.of(), call.getArgument(3), 0));

    mvc.perform(
            get("/daily-remunerations")
                .header("X-Store-ID", "7")
                .param("person_id", "34")
                .param("business_date", "2026-09-30"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_remuneration").value(7000));

    verify(query).personName(7L, 34L);
    verify(query).dailyOrders(eq(7L), eq(34L), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-02-30", "2026-2-01", "0000-01-01", "2026-13-01", "bad", ""})
  void invalidDayIs400(String day) throws Exception {
    mvc.perform(request(day))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").isString());
  }

  @Test
  void offsetBeyondIntIs400AndNegativePageIsNormalized() throws Exception {
    mvc.perform(request("2026-09-30").param("page", "2147483647").param("size", "20"))
        .andExpect(status().isBadRequest());
    mvc.perform(request("2026-09-30").param("page", "-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orders.number").value(0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1"})
  void invalidPersonIdIs400(String id) throws Exception {
    mvc.perform(
            get("/daily-remunerations")
                .header("X-Store-ID", "1")
                .param("person_id", id)
                .param("business_date", "2026-09-30"))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0001-01-01", "2024-02-29", "9999-12-31"})
  void validDateBounds(String day) throws Exception {
    mvc.perform(request(day))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.business_date").value(day));
  }

  @ParameterizedTest
  @CsvSource({"0,20", "-2,20", "101,100"})
  void normalizesPageSize(int requested, int expected) throws Exception {
    mvc.perform(request("2026-09-30").param("size", Integer.toString(requested)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orders.size").value(expected));
  }

  @Test
  void invisibleHistoryIs404() throws Exception {
    when(query.personName(1L, 34L)).thenThrow(new NotFoundException("キャスト本人が見つかりません"));
    mvc.perform(request("2026-09-30")).andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(authorities = "PERM_CAST_MANAGE")
  void unrelatedPermissionDoesNotGrantRead() throws Exception {
    mvc.perform(request("2026-09-30")).andExpect(status().isForbidden());
  }
}
