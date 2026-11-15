package com.kizuna.storeprofile.api;

import static com.kizuna.shared.web.JwtRequests.jwtUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.storeprofile.api.dto.StoreProfileMapperImpl;
import com.kizuna.storeprofile.application.StoreProfileService;
import com.kizuna.storeprofile.domain.StoreProfilePatch;
import com.kizuna.user.application.StoreScopeResolver;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** handler が X-Store-ID で名乗られた店舗をそのまま service へ渡すことの単体テスト。 */
@WebMvcTest(StoreProfileController.class)
@Import({
  StoreProfileControllerTest.MethodSecurityConfig.class,
  StoreScopeHolder.class,
  StoreProfileMapperImpl.class
})
class StoreProfileControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final Long STORE_ID = 7L;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private StoreProfileService storeProfileService;

  // CurrentStoreArgumentResolver（引数解決器）は @WebMvcTest に自動で取り込まれるため、
  // その依存もモックで満たす必要がある。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @BeforeEach
  void installStoreScope() {
    when(storeScopeResolver.resolve(any())).thenReturn(new StoreScope(Set.of(STORE_ID)));
  }

  @Test
  @DisplayName("店舗設定の取得は名乗られた店舗の id で service を呼ぶこと")
  void getPassesTheCurrentStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            get("/config")
                .header("X-Store-ID", STORE_ID.toString())
                .with(jwtUser(42L, "PERM_STORE_PROFILE_MANAGE")))
        .andExpect(status().isOk());

    verify(storeProfileService).get(STORE_ID);
  }

  @Test
  @DisplayName("店舗設定の更新は名乗られた店舗の id で service を呼ぶこと")
  void updatePassesTheCurrentStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            put("/config")
                .header("X-Store-ID", STORE_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"店舗紹介\"}")
                .with(jwtUser(42L, "PERM_STORE_PROFILE_MANAGE")))
        .andExpect(status().isOk());

    verify(storeProfileService).update(eq(STORE_ID), eq(42L), any(StoreProfilePatch.class));
  }

  @Test
  @DisplayName("匿名の公開店舗設定もヘッダで名乗られた店舗の id で service を呼ぶこと")
  @WithAnonymousUser
  void publicGetPassesTheNamedStore() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(get("/config/public").header("X-Store-ID", STORE_ID.toString()))
        .andExpect(status().isOk());

    verify(storeProfileService).get(STORE_ID);
  }
}
