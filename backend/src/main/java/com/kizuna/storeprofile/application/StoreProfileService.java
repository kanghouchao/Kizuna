package com.kizuna.storeprofile.application;

import com.kizuna.storeprofile.api.dto.StoreProfileResponse;
import com.kizuna.storeprofile.api.dto.StoreProfileUpdateRequest;

public interface StoreProfileService {
  StoreProfileResponse get();

  StoreProfileResponse update(StoreProfileUpdateRequest request);
}
