package com.kizuna.storeprofile.application;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
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
public class StoreProfileService {

  private final StoreProfileRepository storeProfileRepository;
  private final StoreContext tenantContext;
  private final StoreProfileMapper storeProfileMapper;

  @StoreScoped
  @Transactional(readOnly = true)
  public StoreProfileResponse get() {
    Long tenantId = tenantContext.getStoreId();
    StoreProfile config =
        storeProfileRepository
            .findByStoreId(tenantId)
            .orElseThrow(() -> new ServiceException("テナント設定が見つかりません"));
    return storeProfileMapper.toResponse(config);
  }

  @StoreScoped
  @Transactional
  public StoreProfileResponse update(StoreProfileUpdateRequest request) {
    Long tenantId = tenantContext.getStoreId();
    StoreProfile config =
        storeProfileRepository
            .findByStoreId(tenantId)
            .orElseThrow(() -> new ServiceException("テナント設定が見つかりません"));
    storeProfileMapper.updateEntityFromRequest(request, config);
    return storeProfileMapper.toResponse(storeProfileRepository.saveAndFlush(config));
  }
}
