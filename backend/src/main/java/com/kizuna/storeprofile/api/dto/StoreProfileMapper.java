package com.kizuna.storeprofile.api.dto;

import com.kizuna.storeprofile.domain.StoreProfile;
import com.kizuna.storeprofile.domain.StoreProfilePatch;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface StoreProfileMapper {

  StoreProfileResponse toResponse(StoreProfile config);

  StoreProfilePatch toPatch(StoreProfileUpdateRequest request);
}
