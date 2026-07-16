package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.CastFieldDefinitionCreateRequest;
import com.kizuna.cast.api.dto.CastFieldDefinitionMapper;
import com.kizuna.cast.api.dto.CastFieldDefinitionResponse;
import com.kizuna.cast.api.dto.CastFieldDefinitionUpdateRequest;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.shared.tenancy.TenantScoped;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * カスタムフィールド定義の CRUD ユースケース。
 *
 * <p>重複 key・件数上限はいずれも {@link ServiceException}（400）で統一する。DB 一意制約 {@code (tenant_id, key)}
 * を最終防波堤とし、悲観ロックは導入しない（{@code CastInvitationAcceptanceService} のメール重複チェックと同じ許容パターン）。
 */
@Service
@RequiredArgsConstructor
public class CastFieldDefinitionService {

  /** テナントあたりの定義件数上限。 */
  static final int MAX_DEFINITIONS = 20;

  private final CastFieldDefinitionRepository repository;
  private final CastFieldDefinitionMapper mapper;
  private final TenantContext tenantContext;

  @TenantScoped
  @Transactional(readOnly = true)
  public List<CastFieldDefinitionResponse> list() {
    return repository.findAllByOrderByDisplayOrderAsc().stream().map(mapper::toResponse).toList();
  }

  @TenantScoped
  @Transactional
  public CastFieldDefinitionResponse create(CastFieldDefinitionCreateRequest request) {
    if (repository.existsByKey(request.getKey())) {
      throw new ServiceException("このキーは既に登録されています: " + request.getKey());
    }
    if (repository.count() >= MAX_DEFINITIONS) {
      throw new ServiceException("カスタムフィールド定義は最大" + MAX_DEFINITIONS + "件までです");
    }
    Integer max = repository.findMaxDisplayOrder();
    int nextOrder = max == null ? 0 : max + 1;
    CastFieldDefinition definition =
        CastFieldDefinition.builder()
            .key(request.getKey())
            .label(request.getLabel())
            .displayOrder(nextOrder)
            .isPublic(Boolean.TRUE.equals(request.getIsPublic()))
            .build();
    definition.setTenantId(tenantContext.getTenantId());
    try {
      return mapper.toResponse(repository.saveAndFlush(definition));
    } catch (DataIntegrityViolationException ex) {
      // 事前チェックをすり抜けた並行 create が (tenant_id, key) 一意制約に当たったレース。
      // 事前チェックと同一の 400（ServiceException）へ変換する（saveAndFlush で違反をこの try 内に顕在化させる）。
      throw new ServiceException("このキーは既に登録されています: " + request.getKey());
    }
  }

  @TenantScoped
  @Transactional
  public CastFieldDefinitionResponse update(String id, CastFieldDefinitionUpdateRequest request) {
    CastFieldDefinition definition =
        repository
            .findById(id)
            .orElseThrow(() -> new ServiceException("カスタムフィールド定義が見つかりません: " + id));
    definition.apply(mapper.toPatch(request));
    return mapper.toResponse(repository.save(definition));
  }

  @TenantScoped
  @Transactional
  public void delete(String id) {
    if (!repository.existsById(id)) {
      throw new ServiceException("カスタムフィールド定義が見つかりません: " + id);
    }
    repository.deleteById(id);
  }
}
