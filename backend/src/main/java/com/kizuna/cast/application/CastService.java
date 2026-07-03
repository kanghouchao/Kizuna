package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.CastCreateRequest;
import com.kizuna.cast.api.dto.CastResponse;
import com.kizuna.cast.api.dto.CastUpdateRequest;
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
