package com.cms.service.tenant;

import com.cms.config.TenantScoped;
import com.cms.exception.ServiceException;
import com.cms.model.dto.tenant.girl.GirlCreateRequest;
import com.cms.model.dto.tenant.girl.GirlResponse;
import com.cms.model.dto.tenant.girl.GirlUpdateRequest;
import com.cms.model.entity.tenant.Girl;
import com.cms.repository.tenant.GirlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GirlServiceImpl implements GirlService {

  private final GirlRepository girlRepository;

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public Page<GirlResponse> list(String search, Pageable pageable) {
    if (search != null && !search.isEmpty()) {
      return girlRepository.findByNameContainingIgnoreCase(search, pageable).map(this::toResponse);
    }
    return girlRepository.findAll(pageable).map(this::toResponse);
  }

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public GirlResponse get(String id) {
    return girlRepository
        .findById(id)
        .map(this::toResponse)
        .orElseThrow(() -> new ServiceException("Girl not found: " + id));
  }

  @Override
  @TenantScoped
  @Transactional
  public GirlResponse create(GirlCreateRequest request) {
    Girl girl = new Girl();
    girl.setName(request.getName());
    girl.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
    return toResponse(girlRepository.save(girl));
  }

  @Override
  @TenantScoped
  @Transactional
  public GirlResponse update(String id, GirlUpdateRequest request) {
    Girl girl =
        girlRepository
            .findById(id)
            .orElseThrow(() -> new ServiceException("Girl not found: " + id));

    if (request.getName() != null) girl.setName(request.getName());
    if (request.getStatus() != null) girl.setStatus(request.getStatus());

    return toResponse(girlRepository.save(girl));
  }

  @Override
  @TenantScoped
  @Transactional
  public void delete(String id) {
    if (!girlRepository.existsById(id)) {
      throw new ServiceException("Girl not found: " + id);
    }
    girlRepository.deleteById(id);
  }

  private GirlResponse toResponse(Girl girl) {
    return GirlResponse.builder()
        .id(girl.getId())
        .name(girl.getName())
        .status(girl.getStatus())
        .build();
  }
}
