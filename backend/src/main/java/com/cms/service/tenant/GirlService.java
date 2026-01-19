package com.cms.service.tenant;

import com.cms.model.dto.tenant.girl.GirlCreateRequest;
import com.cms.model.dto.tenant.girl.GirlResponse;
import com.cms.model.dto.tenant.girl.GirlUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GirlService {
  Page<GirlResponse> list(String search, Pageable pageable);

  GirlResponse get(String id);

  GirlResponse create(GirlCreateRequest request);

  GirlResponse update(String id, GirlUpdateRequest request);

  void delete(String id);
}
