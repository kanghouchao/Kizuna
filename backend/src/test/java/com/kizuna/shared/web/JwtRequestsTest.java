package com.kizuna.shared.web;

import static com.kizuna.shared.web.JwtRequests.jwtUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.security.Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 共用の JWT 認証が、handler の利用者 ID と入口の権限判定の両方に効き、次のテストへ残らないことの単体テスト。 */
@WebMvcTest(JwtRequestsTest.ProbeController.class)
@Import({
  JwtRequestsTest.MethodSecurityConfig.class,
  JwtRequestsTest.ProbeController.class,
  StoreScopeHolder.class
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JwtRequestsTest {

  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @RestController
  static class ProbeController {
    @GetMapping("/jwt-requests-probe")
    @PreAuthorize("hasAuthority('PERM_PROBE')")
    public long userId(Principal principal) {
      return JwtClaims.userIdOf(principal);
    }
  }

  @Autowired private MockMvc mockMvc;

  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreScopeResolver storeScopeResolver;

  @Test
  @Order(1)
  @DisplayName("指定した利用者 ID が handler の JwtClaims.userIdOf から返り、認証は要求の後も同じテストの間は残ること")
  void handlerReadsTheGivenUserId() throws Exception {
    mockMvc
        .perform(get("/jwt-requests-probe").with(jwtUser(42L, "PERM_OTHER", "PERM_PROBE")))
        .andExpect(status().isOk())
        .andExpect(content().string("42"));
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
  }

  @Test
  @Order(2)
  @DisplayName("前のテストの認証が残っていないこと")
  void authenticationDoesNotLeakIntoTheNextTest() {
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @Order(3)
  @DisplayName("指定した権限に無い権限を求める入口は 403 になること")
  void preAuthorizeEvaluatesTheGivenAuthorities() throws Exception {
    mockMvc
        .perform(get("/jwt-requests-probe").with(jwtUser(42L, "PERM_OTHER")))
        .andExpect(status().isForbidden());
  }
}
