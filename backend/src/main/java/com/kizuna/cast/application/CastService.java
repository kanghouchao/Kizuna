package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.CastCreateRequest;
import com.kizuna.cast.api.dto.CastMapper;
import com.kizuna.cast.api.dto.CastPublicResponse;
import com.kizuna.cast.api.dto.CastResponse;
import com.kizuna.cast.api.dto.CastSummaryResponse;
import com.kizuna.cast.api.dto.CastUpdateRequest;
import com.kizuna.cast.domain.AttendanceReferenceCheck;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionRepository;
import com.kizuna.cast.domain.CastInvitationStatus;
import com.kizuna.cast.domain.CastPatch;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScoped;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
  private final CastInvitationService castInvitationService;
  private final CastFieldDefinitionRepository castFieldDefinitionRepository;
  private final AttendanceReferenceCheck attendanceReferenceCheck;

  @StoreScoped
  @Transactional(readOnly = true)
  public Page<CastSummaryResponse> list(String search, Pageable pageable) {
    Page<Cast> page =
        search != null
            ? castRepository.findByNameContainingIgnoreCase(search, pageable)
            : castRepository.findAll(pageable);
    Map<String, CastInvitationStatus> statuses =
        castInvitationService.deriveStatuses(page.getContent());
    return page.map(cast -> castMapper.toSummaryResponse(cast, statuses.get(cast.getId())));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CastResponse get(String id) {
    Cast cast =
        castRepository.findById(id).orElseThrow(() -> new NotFoundException("キャストが見つかりません"));
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
    // store_id は StoreScopeStampListener が @PrePersist で採番する
    Cast cast = castMapper.toEntity(request);
    return castMapper.toResponse(castRepository.save(cast));
  }

  @StoreScoped
  @Transactional
  public CastResponse update(String id, CastUpdateRequest request) {
    Cast cast =
        castRepository.findById(id).orElseThrow(() -> new NotFoundException("キャストが見つかりません"));

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

  /**
   * キャストを削除する。受注から参照されている行は削除できない — 過去の受注が誰の担当だったかは売上の根拠であり、 参照ごと消えてよいものではない。在籍しなくなったキャストは削除ではなく
   * INACTIVE で表す。
   *
   * <p>当日実績からの参照も同じく削除を止める。こちらは外部キー任せにできない — キャストの削除はシフトへ連鎖するため、
   * 実績が先に当たるのはシフト側の外部キーでありうる。どちらが鳴るかで断りの文言が変わらないよう、実績は前置の判定で見る（ADR 0014）。 判定がシフト経由の参照まで数える理由は
   * {@link AttendanceReferenceCheck} 側にある。
   */
  @StoreScoped
  @Transactional
  public void delete(String id) {
    if (!castRepository.existsById(id)) {
      throw new NotFoundException("キャストが見つかりません");
    }
    if (attendanceReferenceCheck.existsForCast(id)) {
      throw new ConflictException("実績が記録されているキャストは削除できません。在籍停止に変更してください");
    }
    try {
      castRepository.deleteById(id);
      // flush が要る理由は CustomerService#delete と同じ（commit まで遅れると catch を素通りする）。
      castRepository.flush();
    } catch (DataIntegrityViolationException ex) {
      // 受注 FK 違反は日常操作で当たる（受注のあるキャストの削除）ので、次の一手の読める 409 へ写す。
      // 他の整合性違反は実装欠陥であり、握りつぶさず全域ハンドラの分類に委ねる。
      throw IntegrityViolations.translate(
          ex,
          Map.of(
              DbConstraint.FK_T_ORDERS_CAST,
              () -> new ConflictException("受注が紐づいているキャストは削除できません。在籍停止に変更してください")));
    }
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
