package com.kizuna.service.tenant;

import com.kizuna.model.dto.tenant.cast.CastCreateRequest;
import com.kizuna.model.dto.tenant.cast.CastResponse;
import com.kizuna.model.dto.tenant.cast.CastUpdateRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CastService {
  Page<CastResponse> list(String search, Pageable pageable);

  CastResponse get(String id);

  CastResponse create(CastCreateRequest request);

  CastResponse update(String id, CastUpdateRequest request);

  void delete(String id);

  List<CastResponse> listActive();
}
