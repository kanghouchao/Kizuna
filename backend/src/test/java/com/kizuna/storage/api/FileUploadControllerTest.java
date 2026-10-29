package com.kizuna.storage.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.storage.api.dto.FileUploadResponse;
import com.kizuna.storage.application.FileStorageService;
import com.kizuna.storage.application.StoredFileAudit;
import com.kizuna.user.domain.PermissionCode;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
  @Mock private StoredFileAudit storedFileAudit;

  private FileUploadController controller;

  @BeforeEach
  void setUp() {
    AppProperties appProperties = new AppProperties();
    appProperties.setUpload(new AppProperties.Upload());
    controller = new FileUploadController(fileStorageService, appProperties, storedFileAudit);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
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
  @DisplayName("ASSET_MANAGE 能力のトークンは店舗を名乗らなくても platform 配下に保存すること")
  void upload_storesUnderPlatformForAssetManage() {
    authenticateWithAuthorities(PermissionCode.ASSET_MANAGE.authority());
    when(fileStorageService.store("platform", "public", file)).thenReturn("public/platform/x.jpg");
    when(file.getOriginalFilename()).thenReturn("x.jpg");
    when(file.getSize()).thenReturn(3L);

    ResponseEntity<FileUploadResponse> res = controller.upload(null, file, "public");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    verify(fileStorageService).store("platform", "public", file);
    assertThat(res.getBody().getUrl()).isEqualTo("/static/uploads/public/platform/x.jpg");
  }

  @Test
  @DisplayName("ASSET_MANAGE の無いトークンは店舗を名乗らない場合、platform に保存せず拒否すること")
  void upload_rejectsWithoutAssetManageAndStore() {
    authenticateWithAuthorities("PERM_ORDER_MANAGE");

    assertThatThrownBy(() -> controller.upload(null, file, "public"))
        .isInstanceOf(AccessDeniedException.class);

    verify(fileStorageService, never()).store(any(), any(), any());
  }

  @Test
  @DisplayName("店舗を名乗ればその店舗配下に保存すること")
  void upload_storesUnderStorePrefixWhenStoreNamed() {
    authenticateWithAuthorities(PermissionCode.STORE_PROFILE_MANAGE.authority());
    when(fileStorageService.store("5", "public", file)).thenReturn("public/5/y.jpg");
    when(file.getOriginalFilename()).thenReturn("y.jpg");
    when(file.getSize()).thenReturn(4L);

    ResponseEntity<FileUploadResponse> res = controller.upload(5L, file, "public");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    verify(fileStorageService).store("5", "public", file);
  }

  @Test
  @DisplayName("保存が成功した後に、保存先の相対パス・bucket・サイズ・種別で監査を依頼すること")
  void upload_recordsAuditAfterStore() {
    authenticateWithAuthorities(PermissionCode.STORE_PROFILE_MANAGE.authority());
    when(fileStorageService.store("5", "public", file)).thenReturn("public/5/y.jpg");
    when(file.getOriginalFilename()).thenReturn("y.jpg");
    when(file.getSize()).thenReturn(4L);
    when(file.getContentType()).thenReturn("image/jpeg");

    controller.upload(5L, file, "public");

    var order = inOrder(fileStorageService, storedFileAudit);
    order.verify(fileStorageService).store("5", "public", file);
    order.verify(storedFileAudit).recordUpload(5L, "public/5/y.jpg", "public", 4L, "image/jpeg");
  }

  @Test
  @DisplayName("保存が失敗したら監査を依頼しないこと")
  void upload_doesNotRecordAuditWhenStoreFails() {
    authenticateWithAuthorities(PermissionCode.STORE_PROFILE_MANAGE.authority());
    when(fileStorageService.store("5", "public", file)).thenThrow(new IllegalStateException("s3"));

    assertThatThrownBy(() -> controller.upload(5L, file, "public"))
        .isInstanceOf(IllegalStateException.class);

    verifyNoInteractions(storedFileAudit);
  }

  @Test
  @DisplayName("店舗を名乗っても、店舗プロフィール・キャスト管理のどちらの能力も無ければ保存せず拒否すること")
  void upload_rejectsStoreWithoutProfileOrCastManage() {
    authenticateWithAuthorities(PermissionCode.ASSET_MANAGE.authority(), "PERM_ORDER_MANAGE");

    assertThatThrownBy(() -> controller.upload(5L, file, "public"))
        .isInstanceOf(AccessDeniedException.class);

    verify(fileStorageService, never()).store(any(), any(), any());
    verifyNoInteractions(storedFileAudit);
  }
}
