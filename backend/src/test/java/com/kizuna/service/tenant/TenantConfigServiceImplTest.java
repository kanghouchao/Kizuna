package com.kizuna.service.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.exception.ServiceException;
import com.kizuna.mapper.tenant.TenantConfigMapper;
import com.kizuna.model.dto.tenant.tenantconfig.PartnerLink;
import com.kizuna.model.dto.tenant.tenantconfig.SnsLink;
import com.kizuna.model.dto.tenant.tenantconfig.TenantConfigResponse;
import com.kizuna.model.dto.tenant.tenantconfig.TenantConfigUpdateRequest;
import com.kizuna.model.entity.tenant.TenantConfig;
import com.kizuna.repository.tenant.TenantConfigRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantConfigServiceImplTest {

  @Mock private TenantConfigRepository tenantConfigRepository;
  @Mock private TenantContext tenantContext;
  @Mock private TenantConfigMapper tenantConfigMapper;
  @InjectMocks private TenantConfigServiceImpl tenantConfigService;

  private static final Long TENANT_ID = 1L;

  @BeforeEach
  void setUp() {
    when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
  }

  @Test
  void get_returnsExistingConfig() {
    TenantConfig config = createTestConfig();
    TenantConfigResponse expected = createTestResponse();
    when(tenantConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(config));
    when(tenantConfigMapper.toResponse(config)).thenReturn(expected);

    TenantConfigResponse response = tenantConfigService.get();

    assertThat(response.getId()).isEqualTo(1L);
    assertThat(response.getTemplateKey()).isEqualTo("default");
    assertThat(response.getLogoUrl()).isEqualTo("https://example.com/logo.png");
  }

  @Test
  void get_throwsExceptionIfNotExists() {
    when(tenantConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> tenantConfigService.get())
        .isInstanceOf(ServiceException.class)
        .hasMessage("テナント設定が見つかりません");
  }

  @Test
  void update_modifiesFields() {
    TenantConfig config = createTestConfig();
    when(tenantConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(config));
    when(tenantConfigRepository.saveAndFlush(any())).thenReturn(config);

    TenantConfigUpdateRequest request = new TenantConfigUpdateRequest();
    request.setLogoUrl("https://example.com/new-logo.png");
    request.setDescription("Updated description");

    doAnswer(
            invocation -> {
              TenantConfigUpdateRequest req = invocation.getArgument(0);
              TenantConfig target = invocation.getArgument(1);
              if (req.getLogoUrl() != null) target.setLogoUrl(req.getLogoUrl());
              if (req.getDescription() != null) target.setDescription(req.getDescription());
              return null;
            })
        .when(tenantConfigMapper)
        .updateEntityFromRequest(eq(request), eq(config));

    TenantConfigResponse expected = createTestResponse();
    expected.setLogoUrl("https://example.com/new-logo.png");
    expected.setDescription("Updated description");
    when(tenantConfigMapper.toResponse(config)).thenReturn(expected);

    TenantConfigResponse response = tenantConfigService.update(request);

    assertThat(response.getLogoUrl()).isEqualTo("https://example.com/new-logo.png");
    assertThat(response.getDescription()).isEqualTo("Updated description");
    verify(tenantConfigMapper).updateEntityFromRequest(request, config);
  }

  @Test
  void update_modifiesSnsLinks() {
    TenantConfig config = createTestConfig();
    when(tenantConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(config));
    when(tenantConfigRepository.saveAndFlush(any())).thenReturn(config);

    TenantConfigUpdateRequest request = new TenantConfigUpdateRequest();
    List<SnsLink> newSnsLinks =
        List.of(SnsLink.builder().platform("twitter").url("https://twitter.com/test").build());
    request.setSnsLinks(newSnsLinks);

    TenantConfigResponse expected = createTestResponse();
    expected.setSnsLinks(newSnsLinks);
    when(tenantConfigMapper.toResponse(config)).thenReturn(expected);

    TenantConfigResponse response = tenantConfigService.update(request);

    assertThat(response.getSnsLinks()).hasSize(1);
    assertThat(response.getSnsLinks().get(0).getPlatform()).isEqualTo("twitter");
    verify(tenantConfigMapper).updateEntityFromRequest(request, config);
  }

  @Test
  void update_modifiesPartnerLinks() {
    TenantConfig config = createTestConfig();
    when(tenantConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(config));
    when(tenantConfigRepository.saveAndFlush(any())).thenReturn(config);

    TenantConfigUpdateRequest request = new TenantConfigUpdateRequest();
    List<PartnerLink> newPartnerLinks =
        List.of(PartnerLink.builder().name("Partner1").url("https://partner.com").build());
    request.setPartnerLinks(newPartnerLinks);

    TenantConfigResponse expected = createTestResponse();
    expected.setPartnerLinks(newPartnerLinks);
    when(tenantConfigMapper.toResponse(config)).thenReturn(expected);

    TenantConfigResponse response = tenantConfigService.update(request);

    assertThat(response.getPartnerLinks()).hasSize(1);
    assertThat(response.getPartnerLinks().get(0).getName()).isEqualTo("Partner1");
    verify(tenantConfigMapper).updateEntityFromRequest(request, config);
  }

  @Test
  void update_throwsExceptionIfNotExists() {
    when(tenantConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

    TenantConfigUpdateRequest request = new TenantConfigUpdateRequest();

    assertThatThrownBy(() -> tenantConfigService.update(request))
        .isInstanceOf(ServiceException.class)
        .hasMessage("テナント設定が見つかりません");
  }

  private TenantConfig createTestConfig() {
    TenantConfig config = new TenantConfig();
    config.setId(1L);
    config.setTemplateKey("default");
    config.setLogoUrl("https://example.com/logo.png");
    config.setMvType("image");
    config.setSnsLinks(new ArrayList<>());
    config.setPartnerLinks(new ArrayList<>());
    config.setCreatedAt(OffsetDateTime.now());
    config.setUpdatedAt(OffsetDateTime.now());
    return config;
  }

  private TenantConfigResponse createTestResponse() {
    return TenantConfigResponse.builder()
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
