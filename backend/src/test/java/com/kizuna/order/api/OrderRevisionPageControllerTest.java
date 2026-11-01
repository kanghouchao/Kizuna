package com.kizuna.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.api.dto.OrderMapperImpl;
import com.kizuna.order.application.OrderQueryService;
import com.kizuna.order.application.OrderService;
import com.kizuna.order.application.OrderSpecialServices;
import com.kizuna.service.application.OrderServiceCatalog;
import com.kizuna.service.application.OrderServiceCatalog.SelectionKind;
import com.kizuna.service.application.OrderSpecialServiceCatalog;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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

@WebMvcTest({
  OrderCourseController.class,
  OrderSurchargeController.class,
  OrderSpecialServiceController.class
})
@Import({
  OrderRevisionPageControllerTest.MethodSecurityConfig.class,
  StoreScopeHolder.class,
  OrderMapperImpl.class
})
@WithMockUser(authorities = {"PERM_ORDER_MANAGE", "PERM_ORDER_CORRECT"})
class OrderRevisionPageControllerTest {
  private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "occurredAt", "id");

  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mvc;
  @MockitoBean private OrderServiceCatalog catalog;
  @MockitoBean private OrderSpecialServiceCatalog specialCatalog;
  @MockitoBean private OrderSpecialServices specials;
  @MockitoBean private OrderService orders;
  @MockitoBean private OrderQueryService orderQueries;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @BeforeEach
  void store() {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
  }

  @Test
  void courseAndSurchargeRevisionsAreNewestFirstPages() throws Exception {
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(catalog.history(eq(1L), any(SelectionKind.class), eq("基本"), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of()));

    for (String path : List.of("/orders/o1/course-revisions", "/orders/o1/surcharge-revisions")) {
      mvc.perform(get(path).param("search", "基本").header("X-Store-ID", "1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total_elements").value(0));
      assertThat(pageable.getValue().getSort()).isEqualTo(NEWEST_FIRST);
    }
  }

  @Test
  void specialServiceRevisionsAndEventsAreNewestFirstPages() throws Exception {
    var revisions = ArgumentCaptor.forClass(Pageable.class);
    when(specialCatalog.history(eq(1L), any(), revisions.capture()))
        .thenReturn(new PageImpl<>(List.of()));
    var events = ArgumentCaptor.forClass(Pageable.class);
    when(specials.history(eq(1L), eq("o1"), events.capture()))
        .thenReturn(new PageImpl<>(List.of()));

    mvc.perform(get("/orders/o1/special-service-revisions").header("X-Store-ID", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    mvc.perform(get("/orders/o1/special-service-events").header("X-Store-ID", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));

    assertThat(revisions.getValue().getSort()).isEqualTo(NEWEST_FIRST);
    assertThat(events.getValue().getSort()).isEqualTo(NEWEST_FIRST);
  }

  @Test
  void currentCandidatesArePagesInNameOrder() throws Exception {
    var byName = Sort.by("afterTerms.name", "serviceId", "id");
    var selections = ArgumentCaptor.forClass(Pageable.class);
    when(catalog.candidates(eq(1L), any(SelectionKind.class), eq("基本"), selections.capture()))
        .thenReturn(new PageImpl<>(List.of()));
    var specialCandidates = ArgumentCaptor.forClass(Pageable.class);
    when(specialCatalog.candidates(eq(1L), eq("c1"), any(), specialCandidates.capture()))
        .thenReturn(new PageImpl<>(List.of()));

    for (String path : List.of("/orders/course-candidates", "/orders/surcharge-candidates")) {
      mvc.perform(get(path).param("search", "基本").header("X-Store-ID", "1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total_elements").value(0));
      assertThat(selections.getValue()).isEqualTo(PageRequest.of(0, 20, byName));
    }
    mvc.perform(
            get("/orders/special-service-candidates")
                .param("cast_id", "c1")
                .param("size", "101")
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    assertThat(specialCandidates.getValue()).isEqualTo(PageRequest.of(0, 100, byName));
  }

  @Test
  void outOfRangePagesAreRejected() throws Exception {
    mvc.perform(
            get("/orders/o1/special-service-events")
                .param("page", "99999999")
                .param("size", "100")
                .header("X-Store-ID", "1"))
        .andExpect(status().isBadRequest());
  }
}
