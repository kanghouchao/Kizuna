package com.kizuna.service.tenant;

import com.kizuna.config.TenantScoped;
import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.exception.ServiceException;
import com.kizuna.model.dto.tenant.siteconfig.SiteConfigResponse;
import com.kizuna.model.dto.tenant.siteconfig.SiteConfigUpdateRequest;
import com.kizuna.model.entity.central.tenant.Tenant;
import com.kizuna.model.entity.tenant.TenantSiteConfig;
import com.kizuna.repository.central.TenantRepository;
import com.kizuna.repository.tenant.TenantSiteConfigRepository;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SiteConfigServiceImpl implements SiteConfigService {

  private final TenantSiteConfigRepository siteConfigRepository;
  private final TenantRepository tenantRepository;
  private final TenantContext tenantContext;

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public SiteConfigResponse get() {
    Long tenantId = tenantContext.getTenantId();
    TenantSiteConfig config =
        siteConfigRepository
            .findByTenantId(tenantId)
            .orElseGet(() -> createDefaultConfig(tenantId));
    return toResponse(config);
  }

  @Override
  @TenantScoped
  @Transactional
  public SiteConfigResponse update(SiteConfigUpdateRequest request) {
    Long tenantId = tenantContext.getTenantId();
    TenantSiteConfig config =
        siteConfigRepository
            .findByTenantId(tenantId)
            .orElseGet(() -> createDefaultConfig(tenantId));

    if (request.getTemplateKey() != null) {
      config.setTemplateKey(request.getTemplateKey());
    }
    if (request.getLogoUrl() != null) {
      config.setLogoUrl(request.getLogoUrl());
    }
    if (request.getBannerUrl() != null) {
      config.setBannerUrl(request.getBannerUrl());
    }
    if (request.getMvUrl() != null) {
      config.setMvUrl(request.getMvUrl());
    }
    if (request.getMvType() != null) {
      config.setMvType(request.getMvType());
    }
    if (request.getDescription() != null) {
      config.setDescription(request.getDescription());
    }
    if (request.getSnsLinks() != null) {
      config.setSnsLinks(request.getSnsLinks());
    }
    if (request.getPartnerLinks() != null) {
      config.setPartnerLinks(request.getPartnerLinks());
    }

    return toResponse(siteConfigRepository.save(config));
  }

  private TenantSiteConfig createDefaultConfig(Long tenantId) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new ServiceException("テナントが見つかりません: " + tenantId));

    TenantSiteConfig config =
        TenantSiteConfig.builder()
            .tenant(tenant)
            .templateKey("default")
            .mvType("image")
            .snsLinks(new ArrayList<>())
            .partnerLinks(new ArrayList<>())
            .build();

    return siteConfigRepository.save(config);
  }

  private SiteConfigResponse toResponse(TenantSiteConfig config) {
    return SiteConfigResponse.builder()
        .id(config.getId())
        .templateKey(config.getTemplateKey())
        .logoUrl(config.getLogoUrl())
        .bannerUrl(config.getBannerUrl())
        .mvUrl(config.getMvUrl())
        .mvType(config.getMvType())
        .description(config.getDescription())
        .snsLinks(config.getSnsLinks())
        .partnerLinks(config.getPartnerLinks())
        .createdAt(config.getCreatedAt())
        .updatedAt(config.getUpdatedAt())
        .build();
  }
}
