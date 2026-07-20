package com.kizuna.storage.api.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.storescope.StoreContext;
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

  private StoreContext storeContext;
  private FileUploadController controller;

  @BeforeEach
  void setUp() {
    storeContext = new StoreContext();
    AppProperties appProperties = new AppProperties();
    appProperties.setUpload(new AppProperties.Upload());
    controller = new FileUploadController(fileStorageService, storeContext, appProperties);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    storeContext.clear();
  }

  /** 指定した authority 群で認証済みリクエストを模擬する（platform 保存可否は SecurityContext の authority で判定される）。 */
  private void authenticateWithAuthorities(String... authorities) {
    PreAuthenticatedAuthenticationToken authentication =
        new PreAuthenticatedAuthenticationToken(
            "user",
            "token",
            List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @Test
  @DisplayName("PLATFORM_ASSET_MANAGE 能力のトークンは店舗文脈が無くても platform 配下に保存すること")
  void upload_storesUnderPlatformForPlatformAssetManage() {
    authenticateWithAuthorities(Capability.PLATFORM_ASSET_MANAGE.authority());
    when(fileStorageService.store("platform", "public", file)).thenReturn("public/platform/x.jpg");
    when(file.getOriginalFilename()).thenReturn("x.jpg");
    when(file.getSize()).thenReturn(3L);

    ResponseEntity<FileUploadResponse> res = controller.upload(file, "public");

    assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    verify(fileStorageService).store("platform", "public", file);
    assertThat(res.getBody().getUrl()).isEqualTo("/static/uploads/public/platform/x.jpg");
  }

  @Test
  @DisplayName("PLATFORM_ASSET_MANAGE の無いトークンは店舗文脈が無い場合、platform に保存せず拒否すること")
  void upload_rejectsWithoutPlatformAssetManageAndStoreContext() {
    authenticateWithAuthorities("PERM_ORDER_MANAGE");

    assertThatThrownBy(() -> controller.upload(file, "public"))
        .isInstanceOf(AccessDeniedException.class);

    verify(fileStorageService, never()).store(any(), any(), any());
  }

  @Test
  @DisplayName("店舗文脈が解決済みならその店舗配下に保存すること")
  void upload_storesUnderStorePrefixWhenContextResolved() {
    storeContext.setStoreId(5L);
    authenticateWithAuthorities("PERM_ORDER_MANAGE");
    when(fileStorageService.store("5", "public", file)).thenReturn("public/5/y.jpg");
    when(file.getOriginalFilename()).thenReturn("y.jpg");
    when(file.getSize()).thenReturn(4L);

    ResponseEntity<FileUploadResponse> res = controller.upload(file, "public");

    assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    verify(fileStorageService).store("5", "public", file);
  }
}
