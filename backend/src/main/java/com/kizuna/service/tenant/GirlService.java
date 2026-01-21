package com.kizuna.service.tenant;

import com.kizuna.model.dto.tenant.girl.GirlCreateRequest;
import com.kizuna.model.dto.tenant.girl.GirlResponse;
import com.kizuna.model.dto.tenant.girl.GirlUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GirlService {
  Page<GirlResponse> list(String search, Pageable pageable);

  GirlResponse get(String id);

  GirlResponse create(GirlCreateRequest request);

  GirlResponse update(String id, GirlUpdateRequest request);

  void delete(String id);
}
