package com.kizuna.storeprofile.application;

import com.kizuna.shared.exception.NotFoundException;
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
  private final StoreProfileMapper storeProfileMapper;

  @Transactional(readOnly = true)
  public StoreProfileResponse get(Long storeId) {
    StoreProfile config =
        storeProfileRepository
            .findByStoreId(storeId)
            .orElseThrow(() -> new NotFoundException("店舗設定が見つかりません"));
    return storeProfileMapper.toResponse(config);
  }

  @Transactional
  public StoreProfileResponse update(Long storeId, StoreProfileUpdateRequest request) {
    StoreProfile config =
        storeProfileRepository
            .findByStoreId(storeId)
            .orElseThrow(() -> new NotFoundException("店舗設定が見つかりません"));
    storeProfileMapper.updateEntityFromRequest(request, config);
    return storeProfileMapper.toResponse(storeProfileRepository.saveAndFlush(config));
  }
}
