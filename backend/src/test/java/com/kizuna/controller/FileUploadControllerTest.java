package com.kizuna.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.config.AppProperties;
import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.service.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class FileUploadControllerTest {

  private MockMvc mockMvc;

  @Mock private FileStorageService fileStorageService;
  @Mock private TenantContext tenantContext;

  private AppProperties appProperties;

  @BeforeEach
  void setUp() {
    appProperties = new AppProperties();
    AppProperties.Upload upload = new AppProperties.Upload();
    upload.setUrlPrefix("/static/");
    appProperties.setUpload(upload);

    FileUploadController controller =
        new FileUploadController(fileStorageService, tenantContext, appProperties);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void upload_tenantUserUsesTenantIdAsPrefix() throws Exception {
    when(tenantContext.isTenant()).thenReturn(true);
    when(tenantContext.getTenantId()).thenReturn(5L);
    when(fileStorageService.store(eq("5"), eq("public"), any())).thenReturn("public/5/uuid.jpg");

    MockMultipartFile file =
        new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

    mockMvc
        .perform(multipart("/files/upload").file(file).param("bucket", "public"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("/static/public/5/uuid.jpg"))
        .andExpect(jsonPath("$.originalName").value("photo.jpg"))
        .andExpect(jsonPath("$.size").value(4));
  }

  @Test
  void upload_centralUserUsesCentralPrefix() throws Exception {
    when(tenantContext.isTenant()).thenReturn(false);
    when(fileStorageService.store(eq("central"), eq("public"), any()))
        .thenReturn("public/central/uuid.png");

    MockMultipartFile file =
        new MockMultipartFile("file", "logo.png", "image/png", "data".getBytes());

    mockMvc
        .perform(multipart("/files/upload").file(file).param("bucket", "public"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("/static/public/central/uuid.png"))
        .andExpect(jsonPath("$.originalName").value("logo.png"));
  }

  @Test
  void upload_usesDefaultPublicBucket() throws Exception {
    when(tenantContext.isTenant()).thenReturn(true);
    when(tenantContext.getTenantId()).thenReturn(1L);
    when(fileStorageService.store(eq("1"), eq("public"), any())).thenReturn("public/1/uuid.jpg");

    MockMultipartFile file =
        new MockMultipartFile("file", "img.jpg", "image/jpeg", "data".getBytes());

    mockMvc
        .perform(multipart("/files/upload").file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("/static/public/1/uuid.jpg"));
  }

  @Test
  void upload_urlPrefixFromAppProperties() throws Exception {
    appProperties.getUpload().setUrlPrefix("https://cdn.example.com/");

    when(tenantContext.isTenant()).thenReturn(true);
    when(tenantContext.getTenantId()).thenReturn(1L);
    when(fileStorageService.store(eq("1"), eq("private"), any())).thenReturn("private/1/uuid.jpg");

    MockMultipartFile file =
        new MockMultipartFile("file", "img.jpg", "image/jpeg", "data".getBytes());

    mockMvc
        .perform(multipart("/files/upload").file(file).param("bucket", "private"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("https://cdn.example.com/private/1/uuid.jpg"));
  }
}
