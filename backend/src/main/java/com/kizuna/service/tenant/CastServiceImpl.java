package com.kizuna.service.tenant;

import com.kizuna.config.TenantScoped;
import com.kizuna.exception.ServiceException;
import com.kizuna.model.dto.tenant.cast.CastCreateRequest;
import com.kizuna.model.dto.tenant.cast.CastResponse;
import com.kizuna.model.dto.tenant.cast.CastUpdateRequest;
import com.kizuna.model.entity.tenant.Cast;
import com.kizuna.repository.tenant.CastRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CastServiceImpl implements CastService {

  private final CastRepository castRepository;

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public Page<CastResponse> list(String search, Pageable pageable) {
    if (search != null && !search.isEmpty()) {
      return castRepository.findByNameContainingIgnoreCase(search, pageable).map(this::toResponse);
    }
    return castRepository.findAll(pageable).map(this::toResponse);
  }

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public CastResponse get(String id) {
    return castRepository
        .findById(id)
        .map(this::toResponse)
        .orElseThrow(() -> new ServiceException("Cast not found: " + id));
  }

  @Override
  @TenantScoped
  @Transactional
  public CastResponse create(CastCreateRequest request) {
    Cast cast = new Cast();
    cast.setName(request.getName());
    cast.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
    cast.setPhotoUrl(request.getPhotoUrl());
    cast.setIntroduction(request.getIntroduction());
    cast.setAge(request.getAge());
    cast.setHeight(request.getHeight());
    cast.setBust(request.getBust());
    cast.setWaist(request.getWaist());
    cast.setHip(request.getHip());
    cast.setDisplayOrder(request.getDisplayOrder());
    return toResponse(castRepository.save(cast));
  }

  @Override
  @TenantScoped
  @Transactional
  public CastResponse update(String id, CastUpdateRequest request) {
    Cast cast =
        castRepository
            .findById(id)
            .orElseThrow(() -> new ServiceException("Cast not found: " + id));

    if (request.getName() != null) cast.setName(request.getName());
    if (request.getStatus() != null) cast.setStatus(request.getStatus());
    if (request.getPhotoUrl() != null) cast.setPhotoUrl(request.getPhotoUrl());
    if (request.getIntroduction() != null) cast.setIntroduction(request.getIntroduction());
    if (request.getAge() != null) cast.setAge(request.getAge());
    if (request.getHeight() != null) cast.setHeight(request.getHeight());
    if (request.getBust() != null) cast.setBust(request.getBust());
    if (request.getWaist() != null) cast.setWaist(request.getWaist());
    if (request.getHip() != null) cast.setHip(request.getHip());
    if (request.getDisplayOrder() != null) cast.setDisplayOrder(request.getDisplayOrder());

    return toResponse(castRepository.save(cast));
  }

  @Override
  @TenantScoped
  @Transactional
  public void delete(String id) {
    if (!castRepository.existsById(id)) {
      throw new ServiceException("Cast not found: " + id);
    }
    castRepository.deleteById(id);
  }

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public List<CastResponse> listActive() {
    return castRepository.findByStatusOrderByDisplayOrderAsc("ACTIVE").stream()
        .map(this::toResponse)
        .toList();
  }

  private CastResponse toResponse(Cast cast) {
    return CastResponse.builder()
        .id(cast.getId())
        .name(cast.getName())
        .status(cast.getStatus())
        .photoUrl(cast.getPhotoUrl())
        .introduction(cast.getIntroduction())
        .age(cast.getAge())
        .height(cast.getHeight())
        .bust(cast.getBust())
        .waist(cast.getWaist())
        .hip(cast.getHip())
        .displayOrder(cast.getDisplayOrder())
        .createdAt(cast.getCreatedAt())
        .updatedAt(cast.getUpdatedAt())
        .build();
  }
}
