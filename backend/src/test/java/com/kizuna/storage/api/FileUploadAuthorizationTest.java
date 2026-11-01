package com.kizuna.storage.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.storage.application.FileStorageService;
import com.kizuna.storage.application.StoredFileAudit;
import com.kizuna.user.application.StoreScopeResolver;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

@WebMvcTest(FileUploadController.class)
@Import({FileUploadAuthorizationTest.Config.class, StoreScopeHolder.class})
class FileUploadAuthorizationTest {
  @TestConfiguration
  @EnableMethodSecurity
  @EnableWebSecurity
  static class Config {
    @Bean
    AppProperties appProperties() {
      return new AppProperties();
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
      return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }
  }

  @Autowired MockMvc mvc;
  @MockitoBean FileStorageService storage;
  @MockitoBean StoredFileAudit audit;
  @MockitoBean StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @BeforeEach
  void stubs() {
    when(storeScopeResolver.resolve(any())).thenReturn(new StoreScope(false, Set.of(1L)));
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(storage.store(eq(1L), eq("public"), any(), any())).thenReturn("public/1/x/y.png");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PERM_STORE_PROFILE_MANAGE", "PERM_CAST_MANAGE", "PERM_ARTICLE_MANAGE"})
  void holderOfAnImageManagingPermissionCanUpload(String authority) throws Exception {
    mvc.perform(upload(authority)).andExpect(status().isCreated());
  }

  @ParameterizedTest
  @ValueSource(strings = {"PERM_REVIEW_VIEW", "PERM_CAST_VIEW"})
  void otherPermissionsAreRefused(String authority) throws Exception {
    mvc.perform(upload(authority)).andExpect(status().isForbidden());
  }

  private static RequestBuilder upload(String authority) {
    return multipart("/files")
        .file(new MockMultipartFile("file", "y.png", "image/png", new byte[] {1, 2, 3}))
        .param("purpose", "article-cover")
        .header("X-Store-ID", "1")
        .with(jwt().authorities(new SimpleGrantedAuthority(authority)));
  }
}
