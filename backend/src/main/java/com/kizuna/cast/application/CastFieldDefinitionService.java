package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.CastFieldDefinitionCreateRequest;
import com.kizuna.cast.api.dto.CastFieldDefinitionMapper;
import com.kizuna.cast.api.dto.CastFieldDefinitionResponse;
import com.kizuna.cast.api.dto.CastFieldDefinitionUpdateRequest;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionRepository;
import com.kizuna.cast.domain.CastProfile;
import com.kizuna.cast.domain.CastProfileRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private final CastEnrollmentService enrollmentService;
  private final CastFieldDefinitionMapper mapper;
  private final StoreRepository storeRepository;
  private final StoreContext storeContext;
  private final CastEnrollmentRepository enrollmentRepository;
  private final CastProfileRepository profileRepository;
  private final BusinessAudit audit;

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
    repository.saveAndFlush(definition);
    audit.recordCurrent(
        definition.getStoreId(),
        "CAST_FIELD_DEFINITION_CREATED",
        "CAST_FIELD_DEFINITION",
        definition.getId(),
        Map.of(),
        snapshot(definition));
    return mapper.toResponse(definition);
  }

  private static Map<String, String> snapshot(CastFieldDefinition definition) {
    return Map.of(
        "exists",
        "true",
        "is_public",
        String.valueOf(definition.getIsPublic()),
        "display_order",
        String.valueOf(definition.getDisplayOrder()),
        "version",
        Objects.toString(definition.getVersion(), ""));
  }

  @StoreScoped
  @Transactional
  public CastFieldDefinitionResponse update(String id, CastFieldDefinitionUpdateRequest request) {
    storeRepository.lockCastFields(storeContext.getStoreId());
    CastFieldDefinition definition =
        repository.findById(id).orElseThrow(() -> new NotFoundException("カスタムフィールド定義が見つかりません"));
    var before = snapshot(definition);
    String previousLabel = definition.getLabel();
    definition.apply(mapper.toPatch(request));
    boolean labelChanged = !Objects.equals(previousLabel, definition.getLabel());
    if (labelChanged || !before.equals(snapshot(definition))) {
      repository.save(definition);
      repository.flush();
      var after = new HashMap<>(snapshot(definition));
      if (labelChanged) after.put("redacted_fields_changed", "label");
      audit.recordCurrent(
          definition.getStoreId(),
          "CAST_FIELD_DEFINITION_UPDATED",
          "CAST_FIELD_DEFINITION",
          id,
          before,
          after);
    }
    return mapper.toResponse(definition);
  }

  @StoreScoped
  @Transactional
  public void delete(String id, String actorEmail) {
    storeRepository.lockCastFields(storeContext.getStoreId());
    CastFieldDefinition definition =
        repository.findById(id).orElseThrow(() -> new NotFoundException("カスタムフィールド定義が見つかりません"));
    var before = snapshot(definition);
    String key = definition.getKey();
    var affectedEnrollments =
        enrollmentRepository.findAllForUpdate().stream()
            .filter(enrollment -> enrollment.getCustomFields().containsKey(key))
            .toList();
    var enrollmentBefore = new HashMap<String, Map<String, String>>();
    affectedEnrollments.forEach(
        enrollment -> enrollmentBefore.put(enrollment.getId(), snapshot(enrollment)));
    var savedSnapshots =
        enrollmentService.removeInternalField(affectedEnrollments, key, actorEmail);
    var affectedProfiles =
        profileRepository.findAll().stream()
            .filter(profile -> profile.getCustomFields().containsKey(key))
            .toList();
    var profileBefore = new HashMap<String, Map<String, String>>();
    affectedProfiles.forEach(
        profile -> {
          profileBefore.put(profile.getId(), snapshot(profile));
          profile.removeCustomField(key);
        });
    repository.deleteById(id);
    repository.flush();
    audit.record(
        actorEmail,
        definition.getStoreId(),
        "CAST_FIELD_DEFINITION_DELETED",
        "CAST_FIELD_DEFINITION",
        id,
        null,
        null,
        before,
        Map.of("exists", "false"));
    var snapshotIds = new HashMap<String, String>();
    savedSnapshots.forEach(saved -> snapshotIds.put(saved.getEnrollmentId(), saved.getId()));
    for (CastEnrollment enrollment : affectedEnrollments) {
      var after = new HashMap<>(snapshot(enrollment));
      after.put("snapshot_id", snapshotIds.get(enrollment.getId()));
      after.put("redacted_fields_changed", "internal_custom_fields");
      audit.record(
          actorEmail,
          enrollment.getStoreId(),
          "CAST_INTERNAL_FIELD_REMOVED",
          "CAST_ENROLLMENT",
          enrollment.getId(),
          "CAST_FIELD_DEFINITION",
          id,
          enrollmentBefore.get(enrollment.getId()),
          after);
    }
    for (CastProfile profile : affectedProfiles) {
      var after = new HashMap<>(snapshot(profile));
      after.put("redacted_fields_changed", "public_custom_fields");
      audit.record(
          actorEmail,
          profile.getStoreId(),
          "CAST_PROFILE_FIELD_REMOVED",
          "CAST_PROFILE",
          profile.getId(),
          "CAST_FIELD_DEFINITION",
          id,
          profileBefore.get(profile.getId()),
          after);
    }
  }

  private static Map<String, String> snapshot(CastEnrollment enrollment) {
    return Map.of("exists", "true", "version", Objects.toString(enrollment.getVersion(), ""));
  }

  private static Map<String, String> snapshot(CastProfile profile) {
    return Map.of(
        "exists",
        "true",
        "enrollment_id",
        profile.getEnrollmentId(),
        "version",
        Objects.toString(profile.getVersion(), ""));
  }
}
