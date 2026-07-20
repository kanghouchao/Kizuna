package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.CastCreateRequest;
import com.kizuna.cast.api.dto.CastMapper;
import com.kizuna.cast.api.dto.CastPublicResponse;
import com.kizuna.cast.api.dto.CastResponse;
import com.kizuna.cast.api.dto.CastUpdateRequest;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionRepository;
import com.kizuna.cast.domain.CastInvitationStatus;
import com.kizuna.cast.domain.CastPatch;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.store.domain.StoreRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CastService {

  /** カスタムフィールド値の最大文字数。 */
  static final int MAX_VALUE_LENGTH = 500;

  private final CastRepository castRepository;
  private final CastMapper castMapper;
  private final StoreContext storeContext;
  private final StoreRepository storeRepository;
  private final CastInvitationService castInvitationService;
  private final CastFieldDefinitionRepository castFieldDefinitionRepository;

  @StoreScoped
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

  @StoreScoped
  @Transactional(readOnly = true)
  public CastResponse get(String id) {
    Cast cast =
        castRepository.findById(id).orElseThrow(() -> new ServiceException("キャストが見つかりません: " + id));
    CastInvitationStatus status = castInvitationService.deriveStatuses(List.of(cast)).get(id);
    return castMapper.toResponse(cast, status);
  }

  /**
   * 指定 id のキャストが現在店舗に属するか判定する（他モジュールからの帰属チェック用ポート）。 storeFilter が効くため、他店舗のキャストは存在しないものとして false
   * を返す。
   */
  @StoreScoped
  @Transactional(readOnly = true)
  public boolean existsForCurrentStore(String id) {
    return castRepository.findById(id).isPresent();
  }

  @StoreScoped
  @Transactional
  public CastResponse create(CastCreateRequest request) {
    Cast cast = castMapper.toEntity(request);

    cast.setStoreId(
        storeRepository
            .findById(storeContext.getStoreId())
            .orElseThrow(() -> new ServiceException("店舗が見つかりません"))
            .getId());

    return castMapper.toResponse(castRepository.save(cast));
  }

  @StoreScoped
  @Transactional
  public CastResponse update(String id, CastUpdateRequest request) {
    Cast cast =
        castRepository.findById(id).orElseThrow(() -> new ServiceException("キャストが見つかりません: " + id));

    CastPatch patch = castMapper.toPatch(request);
    if (patch.customFields() != null) {
      validateCustomFields(patch.customFields());
    }
    cast.apply(patch);

    return castMapper.toResponse(castRepository.save(cast));
  }

  /** カスタムフィールド値を検証する。未知 key・値の文字数超過はいずれも {@link ServiceException}（400）。 */
  private void validateCustomFields(Map<String, String> customFields) {
    Set<String> liveKeys =
        castFieldDefinitionRepository.findAllByOrderByDisplayOrderAsc().stream()
            .map(CastFieldDefinition::getKey)
            .collect(Collectors.toSet());
    for (Map.Entry<String, String> entry : customFields.entrySet()) {
      if (!liveKeys.contains(entry.getKey())) {
        throw new ServiceException("未知のカスタムフィールドキーです: " + entry.getKey());
      }
      String value = entry.getValue();
      if (value != null && value.length() > MAX_VALUE_LENGTH) {
        throw new ServiceException(
            "カスタムフィールドの値は" + MAX_VALUE_LENGTH + "文字以内で入力してください: " + entry.getKey());
      }
    }
  }

  @StoreScoped
  @Transactional
  public void delete(String id) {
    if (!castRepository.existsById(id)) {
      throw new ServiceException("キャストが見つかりません: " + id);
    }
    castRepository.deleteById(id);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public List<CastPublicResponse> listActive() {
    List<CastFieldDefinition> publicDefinitions =
        castFieldDefinitionRepository.findByIsPublicTrueOrderByDisplayOrderAsc();
    return castRepository.findByStatusOrderByDisplayOrderAsc("ACTIVE").stream()
        .map(cast -> castMapper.toPublicResponse(cast, publicDefinitions))
        .toList();
  }
}
