package com.kizuna.storage.api.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.storage.api.dto.FileUploadResponse;
import com.kizuna.storage.application.FileStorageService;
import com.kizuna.user.domain.Capability;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class FileUploadControllerTest {

  @Mock private FileStorageService fileStorageService;
  @Mock private MultipartFile file;

  private TenantContext tenantContext;
  private FileUploadController controller;

  @BeforeEach
  void setUp() {
    tenantContext = new TenantContext();
    AppProperties appProperties = new AppProperties();
    appProperties.setUpload(new AppProperties.Upload());
    controller = new FileUploadController(fileStorageService, tenantContext, appProperties);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    tenantContext.clear();
  }

  /** 指定した authority 群で認証済みリクエストを模擬する（central 保存可否は SecurityContext の authority で判定される）。 */
  private void authenticateWithAuthorities(String... authorities) {
    PreAuthenticatedAuthenticationToken authentication =
        new PreAuthenticatedAuthenticationToken(
            "user",
            "token",
            List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @Test
  @DisplayName("PLATFORM_ASSET_MANAGE 能力のトークンはテナント文脈が無くても central 配下に保存すること")
  void upload_storesUnderCentralForPlatformAssetManage() {
    authenticateWithAuthorities(Capability.PLATFORM_ASSET_MANAGE.authority());
    when(fileStorageService.store("central", "public", file)).thenReturn("public/central/x.jpg");
    when(file.getOriginalFilename()).thenReturn("x.jpg");
    when(file.getSize()).thenReturn(3L);

    ResponseEntity<FileUploadResponse> res = controller.upload(file, "public");

    assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    verify(fileStorageService).store("central", "public", file);
    assertThat(res.getBody().getUrl()).isEqualTo("/static/uploads/public/central/x.jpg");
  }

  @Test
  @DisplayName("PLATFORM_ASSET_MANAGE の無いトークンはテナント文脈が無い場合、central に保存せず拒否すること")
  void upload_rejectsWithoutPlatformAssetManageAndTenantContext() {
    authenticateWithAuthorities("PERM_ORDER_MANAGE");

    assertThatThrownBy(() -> controller.upload(file, "public"))
        .isInstanceOf(AccessDeniedException.class);

    verify(fileStorageService, never()).store(any(), any(), any());
  }

  @Test
  @DisplayName("テナント文脈が解決済みならそのテナント配下に保存すること")
  void upload_storesUnderTenantPrefixWhenContextResolved() {
    tenantContext.setTenantId(5L);
    authenticateWithAuthorities("PERM_ORDER_MANAGE");
    when(fileStorageService.store("5", "public", file)).thenReturn("public/5/y.jpg");
    when(file.getOriginalFilename()).thenReturn("y.jpg");
    when(file.getSize()).thenReturn(4L);

    ResponseEntity<FileUploadResponse> res = controller.upload(file, "public");

    assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    verify(fileStorageService).store("5", "public", file);
  }
}
