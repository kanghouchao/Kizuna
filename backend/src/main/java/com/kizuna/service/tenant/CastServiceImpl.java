package com.kizuna.service.tenant;

import com.kizuna.config.TenantScoped;
import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.exception.ServiceException;
import com.kizuna.mapper.tenant.CastMapper;
import com.kizuna.model.dto.tenant.cast.CastCreateRequest;
import com.kizuna.model.dto.tenant.cast.CastResponse;
import com.kizuna.model.dto.tenant.cast.CastUpdateRequest;
import com.kizuna.model.entity.tenant.Cast;
import com.kizuna.repository.central.TenantRepository;
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
  private final CastMapper castMapper;
  private final TenantContext tenantContext;
  private final TenantRepository tenantRepository;

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public Page<CastResponse> list(String search, Pageable pageable) {
    if (search != null && !search.isEmpty()) {
      return castRepository
          .findByNameContainingIgnoreCase(search, pageable)
          .map(castMapper::toResponse);
    }
    return castRepository.findAll(pageable).map(castMapper::toResponse);
  }

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public CastResponse get(String id) {
    return castRepository
        .findById(id)
        .map(castMapper::toResponse)
        .orElseThrow(() -> new ServiceException("キャストが見つかりません: " + id));
  }

  @Override
  @TenantScoped
  @Transactional
  public CastResponse create(CastCreateRequest request) {
    Cast cast = castMapper.toEntity(request);

    cast.setTenant(
        tenantRepository
            .findById(tenantContext.getTenantId())
            .orElseThrow(() -> new ServiceException("テナントが見つかりません")));

    return castMapper.toResponse(castRepository.save(cast));
  }

  @Override
  @TenantScoped
  @Transactional
  public CastResponse update(String id, CastUpdateRequest request) {
    Cast cast =
        castRepository.findById(id).orElseThrow(() -> new ServiceException("キャストが見つかりません: " + id));

    castMapper.updateEntityFromRequest(request, cast);

    return castMapper.toResponse(castRepository.save(cast));
  }

  @Override
  @TenantScoped
  @Transactional
  public void delete(String id) {
    if (!castRepository.existsById(id)) {
      throw new ServiceException("キャストが見つかりません: " + id);
    }
    castRepository.deleteById(id);
  }

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public List<CastResponse> listActive() {
    return castRepository.findByStatusOrderByDisplayOrderAsc("ACTIVE").stream()
        .map(castMapper::toResponse)
        .toList();
  }
}
