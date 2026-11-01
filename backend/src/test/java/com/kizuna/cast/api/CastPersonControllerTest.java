package com.kizuna.cast.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.cast.application.AdminCastService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CastPersonController.class)
@Import({CastPersonControllerTest.MethodSecurityConfig.class, StoreScopeHolder.class})
class CastPersonControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired MockMvc mvc;
  @MockitoBean AdminCastService service;
  @MockitoBean SystemConfigService systemConfigService;
  @MockitoBean StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @Test
  @WithMockUser(authorities = "PERM_CAST_PERSON_VIEW")
  void adminCanSearchPeople() throws Exception {
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(service.list(eq("花"), pageable.capture())).thenReturn(Page.empty());
    mvc.perform(get("/cast-persons").param("search", "花"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.total_elements").value(0));
    assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, 20, Sort.by("id")));
  }

  @Test
  @WithMockUser(authorities = "PERM_CAST_PERSON_VIEW")
  void enrollmentsAreAPageNewestFirstAndRejectOutOfRangePages() throws Exception {
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(service.enrollments(eq(1L), pageable.capture())).thenReturn(Page.empty());
    mvc.perform(get("/cast-persons/1/enrollments").param("size", "0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    assertThat(pageable.getValue())
        .isEqualTo(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    mvc.perform(get("/cast-persons/1/enrollments").param("page", "99999999").param("size", "100"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(authorities = "PERM_CAST_MANAGE")
  void storePermissionCannotReadAnyPersonEndpoint() throws Exception {
    for (String path : new String[] {"", "/1", "/1/enrollments"}) {
      mvc.perform(get("/cast-persons" + path).header("X-Store-ID", "2"))
          .andExpect(status().isForbidden());
    }
  }
}
