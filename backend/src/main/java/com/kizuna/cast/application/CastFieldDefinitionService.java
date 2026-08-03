package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.CastFieldDefinitionCreateRequest;
import com.kizuna.cast.api.dto.CastFieldDefinitionMapper;
import com.kizuna.cast.api.dto.CastFieldDefinitionResponse;
import com.kizuna.cast.api.dto.CastFieldDefinitionUpdateRequest;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScoped;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * カスタムフィールド定義の CRUD ユースケース。
 *
 * <p>事前チェックで検出した重複 key・件数上限は {@link ServiceException}（400）。DB 一意制約 {@code (store_id, key)}
 * を最終防波堤とし、悲観ロックは導入しない（{@code CastInvitationAcceptanceService} のメール重複チェックと同じ許容パターン）。
 */
@Service
@RequiredArgsConstructor
public class CastFieldDefinitionService {

  /** 店舗あたりの定義件数上限。 */
  static final int MAX_DEFINITIONS = 20;

  private final CastFieldDefinitionRepository repository;
  private final CastFieldDefinitionMapper mapper;

  @StoreScoped
  @Transactional(readOnly = true)
  public List<CastFieldDefinitionResponse> list() {
    return repository.findAllByOrderByDisplayOrderAsc().stream().map(mapper::toResponse).toList();
  }

  @StoreScoped
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
    // store_id は StoreScopeStampListener が @PrePersist で採番する
    CastFieldDefinition definition =
        CastFieldDefinition.builder()
            .key(request.getKey())
            .label(request.getLabel())
            .displayOrder(nextOrder)
            .isPublic(Boolean.TRUE.equals(request.getIsPublic()))
            .build();
    // 事前チェックをすり抜けた並行 create が (store_id, key) 一意制約に当たるレースはここで catch しない —
    // CommonExceptionHandler が SQLSTATE で一意違反だけを 409 へ写像し、FK 等の他の整合性違反は
    // 実装欠陥として 500 のまま大きく失敗させる分類を持っているため、そこへ委ねる。
    return mapper.toResponse(repository.saveAndFlush(definition));
  }

  @StoreScoped
  @Transactional
  public CastFieldDefinitionResponse update(String id, CastFieldDefinitionUpdateRequest request) {
    CastFieldDefinition definition =
        repository.findById(id).orElseThrow(() -> new NotFoundException("カスタムフィールド定義が見つかりません"));
    definition.apply(mapper.toPatch(request));
    return mapper.toResponse(repository.save(definition));
  }

  @StoreScoped
  @Transactional
  public void delete(String id) {
    if (!repository.existsById(id)) {
      throw new NotFoundException("カスタムフィールド定義が見つかりません");
    }
    repository.deleteById(id);
  }
}
