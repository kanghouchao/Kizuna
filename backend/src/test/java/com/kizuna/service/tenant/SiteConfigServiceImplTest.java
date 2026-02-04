package com.kizuna.service.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.model.dto.tenant.siteconfig.PartnerLink;
import com.kizuna.model.dto.tenant.siteconfig.SiteConfigResponse;
import com.kizuna.model.dto.tenant.siteconfig.SiteConfigUpdateRequest;
import com.kizuna.model.dto.tenant.siteconfig.SnsLink;
import com.kizuna.model.entity.central.tenant.Tenant;
import com.kizuna.model.entity.tenant.TenantSiteConfig;
import com.kizuna.repository.central.TenantRepository;
import com.kizuna.repository.tenant.TenantSiteConfigRepository;
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
class SiteConfigServiceImplTest {

  @Mock private TenantSiteConfigRepository siteConfigRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private TenantContext tenantContext;
  @InjectMocks private SiteConfigServiceImpl siteConfigService;

  private static final Long TENANT_ID = 1L;

  @BeforeEach
  void setUp() {
    when(tenantContext.getTenantId()).thenReturn(TENANT_ID);
  }

  @Test
  void get_returnsExistingConfig() {
    TenantSiteConfig config = createTestConfig();
    when(siteConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(config));

    SiteConfigResponse response = siteConfigService.get();

    assertThat(response.getId()).isEqualTo(1L);
    assertThat(response.getTemplateKey()).isEqualTo("default");
    assertThat(response.getLogoUrl()).isEqualTo("https://example.com/logo.png");
  }

  @Test
  void get_returnsDefaultConfigWithoutSavingIfNotExists() {
    Tenant tenant = new Tenant();
    tenant.setId(TENANT_ID);
    when(siteConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

    SiteConfigResponse response = siteConfigService.get();

    assertThat(response.getTemplateKey()).isEqualTo("default");
    verify(siteConfigRepository, never()).save(any());
  }

  @Test
  void update_modifiesFields() {
    TenantSiteConfig config = createTestConfig();
    when(siteConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(config));
    when(siteConfigRepository.save(any())).thenReturn(config);

    SiteConfigUpdateRequest request = new SiteConfigUpdateRequest();
    request.setLogoUrl("https://example.com/new-logo.png");
    request.setDescription("Updated description");

    siteConfigService.update(request);

    assertThat(config.getLogoUrl()).isEqualTo("https://example.com/new-logo.png");
    assertThat(config.getDescription()).isEqualTo("Updated description");
  }

  @Test
  void update_modifiesSnsLinks() {
    TenantSiteConfig config = createTestConfig();
    when(siteConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(config));
    when(siteConfigRepository.save(any())).thenReturn(config);

    SiteConfigUpdateRequest request = new SiteConfigUpdateRequest();
    List<SnsLink> newSnsLinks =
        List.of(SnsLink.builder().platform("twitter").url("https://twitter.com/test").build());
    request.setSnsLinks(newSnsLinks);

    siteConfigService.update(request);

    assertThat(config.getSnsLinks()).hasSize(1);
    assertThat(config.getSnsLinks().get(0).getPlatform()).isEqualTo("twitter");
  }

  @Test
  void update_modifiesPartnerLinks() {
    TenantSiteConfig config = createTestConfig();
    when(siteConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(config));
    when(siteConfigRepository.save(any())).thenReturn(config);

    SiteConfigUpdateRequest request = new SiteConfigUpdateRequest();
    List<PartnerLink> newPartnerLinks =
        List.of(PartnerLink.builder().name("Partner1").url("https://partner.com").build());
    request.setPartnerLinks(newPartnerLinks);

    siteConfigService.update(request);

    assertThat(config.getPartnerLinks()).hasSize(1);
    assertThat(config.getPartnerLinks().get(0).getName()).isEqualTo("Partner1");
  }

  private TenantSiteConfig createTestConfig() {
    TenantSiteConfig config = new TenantSiteConfig();
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
}
