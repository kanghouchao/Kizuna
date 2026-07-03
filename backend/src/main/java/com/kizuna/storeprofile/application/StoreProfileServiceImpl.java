package com.kizuna.storeprofile.application;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.shared.tenancy.TenantScoped;
import com.kizuna.storeprofile.api.dto.StoreProfileMapper;
import com.kizuna.storeprofile.api.dto.StoreProfileResponse;
import com.kizuna.storeprofile.api.dto.StoreProfileUpdateRequest;
import com.kizuna.storeprofile.domain.StoreProfile;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreProfileServiceImpl implements StoreProfileService {

  private final StoreProfileRepository storeProfileRepository;
  private final TenantContext tenantContext;
  private final StoreProfileMapper storeProfileMapper;

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public StoreProfileResponse get() {
    Long tenantId = tenantContext.getTenantId();
    StoreProfile config =
        storeProfileRepository
            .findByTenantId(tenantId)
            .orElseThrow(() -> new ServiceException("テナント設定が見つかりません"));
    return storeProfileMapper.toResponse(config);
  }

  @Override
  @TenantScoped
  @Transactional
  public StoreProfileResponse update(StoreProfileUpdateRequest request) {
    Long tenantId = tenantContext.getTenantId();
    StoreProfile config =
        storeProfileRepository
            .findByTenantId(tenantId)
            .orElseThrow(() -> new ServiceException("テナント設定が見つかりません"));
    storeProfileMapper.updateEntityFromRequest(request, config);
    return storeProfileMapper.toResponse(storeProfileRepository.saveAndFlush(config));
  }
}
