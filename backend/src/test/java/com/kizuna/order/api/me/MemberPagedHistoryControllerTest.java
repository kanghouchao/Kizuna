package com.kizuna.order.api.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.api.dto.OrderMapperImpl;
import com.kizuna.order.application.MemberVisitService;
import com.kizuna.order.application.SelfRemunerationService;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({MemberVisitController.class, SelfRemunerationController.class})
@Import({
  MemberPagedHistoryControllerTest.MethodSecurityConfig.class,
  StoreScopeHolder.class,
  OrderMapperImpl.class
})
class MemberPagedHistoryControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mvc;
  @MockitoBean private MemberVisitService visits;
  @MockitoBean private SelfRemunerationService remunerations;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @Test
  @WithMockUser(username = "member@kizuna.test", roles = "MEMBER")
  void visitsAreANewestFirstPage() throws Exception {
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(visits.list(eq("member@kizuna.test"), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of()));

    mvc.perform(get("/me/visits").principal(() -> "member@kizuna.test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
  }

  @Test
  @WithMockUser(username = "cast@kizuna.test", roles = "CAST")
  void remunerationChangesAreANewestFirstPage() throws Exception {
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(remunerations.changes(eq("cast@kizuna.test"), eq("o1"), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of()));

    mvc.perform(get("/me/remunerations/o1/changes").principal(() -> "cast@kizuna.test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, "afterVersion", "id"));
  }

  @Test
  @WithMockUser(username = "cast@kizuna.test", roles = "CAST")
  void remunerationEnrollmentsAndOrdersAreNewestFirstPages() throws Exception {
    var enrollments = ArgumentCaptor.forClass(Pageable.class);
    when(remunerations.enrollments(eq("cast@kizuna.test"), enrollments.capture()))
        .thenReturn(new PageImpl<>(List.of()));
    var orders = ArgumentCaptor.forClass(Pageable.class);
    when(remunerations.list(eq("cast@kizuna.test"), eq(null), orders.capture()))
        .thenReturn(new PageImpl<>(List.of()));

    mvc.perform(get("/me/remuneration-enrollments").principal(() -> "cast@kizuna.test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    mvc.perform(get("/me/remunerations").principal(() -> "cast@kizuna.test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    assertThat(enrollments.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    assertThat(orders.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, "businessDate", "id"));
  }
}
