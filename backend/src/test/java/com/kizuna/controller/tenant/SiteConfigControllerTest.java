package com.kizuna.controller.tenant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kizuna.model.dto.tenant.siteconfig.SiteConfigResponse;
import com.kizuna.model.dto.tenant.siteconfig.SiteConfigUpdateRequest;
import com.kizuna.service.tenant.SiteConfigService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SiteConfigControllerTest {

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @Mock private SiteConfigService siteConfigService;

  @InjectMocks private SiteConfigController controller;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();
  }

  @Test
  void get_returnsConfig() throws Exception {
    SiteConfigResponse response = createTestResponse();
    when(siteConfigService.get()).thenReturn(response);

    mockMvc
        .perform(get("/tenant/site-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.templateKey").value("default"))
        .andExpect(jsonPath("$.logoUrl").value("https://example.com/logo.png"));
  }

  @Test
  void update_returnsUpdatedConfig() throws Exception {
    SiteConfigResponse response = createTestResponse();
    response.setLogoUrl("https://example.com/new-logo.png");
    when(siteConfigService.update(any())).thenReturn(response);

    SiteConfigUpdateRequest request = new SiteConfigUpdateRequest();
    request.setLogoUrl("https://example.com/new-logo.png");

    mockMvc
        .perform(
            put("/tenant/site-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.logoUrl").value("https://example.com/new-logo.png"));
  }

  private SiteConfigResponse createTestResponse() {
    return SiteConfigResponse.builder()
        .id(1L)
        .templateKey("default")
        .logoUrl("https://example.com/logo.png")
        .mvType("image")
        .snsLinks(List.of())
        .partnerLinks(List.of())
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();
  }
}
