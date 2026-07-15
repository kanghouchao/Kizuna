package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.CastCreateRequest;
import com.kizuna.cast.api.dto.CastMapper;
import com.kizuna.cast.api.dto.CastResponse;
import com.kizuna.cast.api.dto.CastUpdateRequest;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastInvitationStatus;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.shared.tenancy.TenantScoped;
import com.kizuna.tenant.domain.TenantRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CastService {

  private final CastRepository castRepository;
  private final CastMapper castMapper;
  private final TenantContext tenantContext;
  private final TenantRepository tenantRepository;
  private final CastInvitationService castInvitationService;

  @TenantScoped
  @Transactional(readOnly = true)
  public Page<CastResponse> list(String search, Pageable pageable) {
    Page<Cast> page =
        search != null && !search.isEmpty()
            ? castRepository.findByNameContainingIgnoreCase(search, pageable)
            : castRepository.findAll(pageable);
    Map<String, CastInvitationStatus> statuses =
        castInvitationService.deriveStatuses(page.getContent());
    return page.map(cast -> castMapper.toResponse(cast, statuses.get(cast.getId())));
  }

  @TenantScoped
  @Transactional(readOnly = true)
  public CastResponse get(String id) {
    Cast cast =
        castRepository.findById(id).orElseThrow(() -> new ServiceException("キャストが見つかりません: " + id));
    CastInvitationStatus status = castInvitationService.deriveStatuses(List.of(cast)).get(id);
    return castMapper.toResponse(cast, status);
  }

  /**
   * 指定 id のキャストが現在テナントに属するか判定する（他モジュールからの帰属チェック用ポート）。 tenantFilter が効くため、他テナントのキャストは存在しないものとして
   * false を返す。
   */
  @TenantScoped
  @Transactional(readOnly = true)
  public boolean existsForCurrentTenant(String id) {
    return castRepository.findById(id).isPresent();
  }

  @TenantScoped
  @Transactional
  public CastResponse create(CastCreateRequest request) {
    Cast cast = castMapper.toEntity(request);

    cast.setTenantId(
        tenantRepository
            .findById(tenantContext.getTenantId())
            .orElseThrow(() -> new ServiceException("テナントが見つかりません"))
            .getId());

    return castMapper.toResponse(castRepository.save(cast));
  }

  @TenantScoped
  @Transactional
  public CastResponse update(String id, CastUpdateRequest request) {
    Cast cast =
        castRepository.findById(id).orElseThrow(() -> new ServiceException("キャストが見つかりません: " + id));

    cast.apply(castMapper.toPatch(request));

    return castMapper.toResponse(castRepository.save(cast));
  }

  @TenantScoped
  @Transactional
  public void delete(String id) {
    if (!castRepository.existsById(id)) {
      throw new ServiceException("キャストが見つかりません: " + id);
    }
    castRepository.deleteById(id);
  }

  @TenantScoped
  @Transactional(readOnly = true)
  public List<CastResponse> listActive() {
    return castRepository.findByStatusOrderByDisplayOrderAsc("ACTIVE").stream()
        .map(castMapper::toResponse)
        .toList();
  }
}
