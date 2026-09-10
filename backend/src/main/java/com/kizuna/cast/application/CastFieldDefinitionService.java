package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.CastFieldDefinitionCreateRequest;
import com.kizuna.cast.api.dto.CastFieldDefinitionMapper;
import com.kizuna.cast.api.dto.CastFieldDefinitionResponse;
import com.kizuna.cast.api.dto.CastFieldDefinitionUpdateRequest;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionRepository;
import com.kizuna.cast.domain.CastProfileRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.store.domain.StoreRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 定義の編集と値の書き込みは同じ店舗行をロックし、削除・再作成による値の再露出を防ぐ。 */
@Service
@RequiredArgsConstructor
public class CastFieldDefinitionService {

  /** 店舗あたりの定義件数上限。 */
  static final int MAX_DEFINITIONS = 20;

  private final CastFieldDefinitionRepository repository;
  private final CastFieldDefinitionMapper mapper;
  private final StoreRepository storeRepository;
  private final StoreContext storeContext;
  private final CastEnrollmentRepository enrollmentRepository;
  private final CastProfileRepository profileRepository;

  @StoreScoped
  @Transactional(readOnly = true)
  public List<CastFieldDefinitionResponse> list() {
    return repository.findAllByOrderByDisplayOrderAsc().stream().map(mapper::toResponse).toList();
  }

  @StoreScoped
  @Transactional
  public CastFieldDefinitionResponse create(CastFieldDefinitionCreateRequest request) {
    storeRepository.lockCastFields(storeContext.getStoreId());
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
    return mapper.toResponse(repository.saveAndFlush(definition));
  }

  @StoreScoped
  @Transactional
  public CastFieldDefinitionResponse update(String id, CastFieldDefinitionUpdateRequest request) {
    storeRepository.lockCastFields(storeContext.getStoreId());
    CastFieldDefinition definition =
        repository.findById(id).orElseThrow(() -> new NotFoundException("カスタムフィールド定義が見つかりません"));
    definition.apply(mapper.toPatch(request));
    return mapper.toResponse(repository.save(definition));
  }

  @StoreScoped
  @Transactional
  public void delete(String id) {
    storeRepository.lockCastFields(storeContext.getStoreId());
    if (!repository.existsById(id)) {
      throw new NotFoundException("カスタムフィールド定義が見つかりません");
    }
    String key = repository.findById(id).orElseThrow().getKey();
    enrollmentRepository.findAll().forEach(enrollment -> enrollment.removeCustomField(key));
    profileRepository.findAll().forEach(profile -> profile.removeCustomField(key));
    repository.deleteById(id);
  }
}
