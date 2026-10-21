package com.kizuna.cast.api.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.cast.application.AdminCastService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminCastController.class)
@Import({
  AdminCastControllerTest.MethodSecurityConfig.class,
  StoreContext.class,
  StoreScopeHolder.class
})
class AdminCastControllerTest {
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
    when(service.list(any(), anyInt(), anyInt())).thenReturn(Page.empty());
    mvc.perform(get("/admin/casts").param("search", "花"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  @WithMockUser(authorities = "PERM_CAST_MANAGE")
  void storePermissionCannotReadAnyPersonEndpoint() throws Exception {
    for (String path : new String[] {"", "/1", "/1/enrollments"}) {
      mvc.perform(get("/admin/casts" + path).header("X-Role", "platform").header("X-Store-ID", "2"))
          .andExpect(status().isForbidden());
    }
  }
}
