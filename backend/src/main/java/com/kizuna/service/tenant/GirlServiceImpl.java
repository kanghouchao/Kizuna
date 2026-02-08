package com.kizuna.service.tenant;

import com.kizuna.config.TenantScoped;
import com.kizuna.exception.ServiceException;
import com.kizuna.model.dto.tenant.girl.GirlCreateRequest;
import com.kizuna.model.dto.tenant.girl.GirlResponse;
import com.kizuna.model.dto.tenant.girl.GirlUpdateRequest;
import com.kizuna.model.entity.tenant.Girl;
import com.kizuna.repository.tenant.GirlRepository;
import java.util.List;
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
    girl.setPhotoUrl(request.getPhotoUrl());
    girl.setIntroduction(request.getIntroduction());
    girl.setAge(request.getAge());
    girl.setHeight(request.getHeight());
    girl.setBust(request.getBust());
    girl.setWaist(request.getWaist());
    girl.setHip(request.getHip());
    girl.setDisplayOrder(request.getDisplayOrder());
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
    if (request.getPhotoUrl() != null) girl.setPhotoUrl(request.getPhotoUrl());
    if (request.getIntroduction() != null) girl.setIntroduction(request.getIntroduction());
    if (request.getAge() != null) girl.setAge(request.getAge());
    if (request.getHeight() != null) girl.setHeight(request.getHeight());
    if (request.getBust() != null) girl.setBust(request.getBust());
    if (request.getWaist() != null) girl.setWaist(request.getWaist());
    if (request.getHip() != null) girl.setHip(request.getHip());
    if (request.getDisplayOrder() != null) girl.setDisplayOrder(request.getDisplayOrder());

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

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public List<GirlResponse> listActive() {
    return girlRepository.findByStatusOrderByDisplayOrderAsc("ACTIVE").stream()
        .map(this::toResponse)
        .toList();
  }

  private GirlResponse toResponse(Girl girl) {
    return GirlResponse.builder()
        .id(girl.getId())
        .name(girl.getName())
        .status(girl.getStatus())
        .photoUrl(girl.getPhotoUrl())
        .introduction(girl.getIntroduction())
        .age(girl.getAge())
        .height(girl.getHeight())
        .bust(girl.getBust())
        .waist(girl.getWaist())
        .hip(girl.getHip())
        .displayOrder(girl.getDisplayOrder())
        .createdAt(girl.getCreatedAt())
        .updatedAt(girl.getUpdatedAt())
        .build();
  }
}
