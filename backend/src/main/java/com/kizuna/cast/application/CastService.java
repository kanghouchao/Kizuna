package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.CastCreateRequest;
import com.kizuna.cast.api.dto.CastMapper;
import com.kizuna.cast.api.dto.CastPublicResponse;
import com.kizuna.cast.api.dto.CastPublicationResponse;
import com.kizuna.cast.api.dto.CastResponse;
import com.kizuna.cast.api.dto.CastSummaryResponse;
import com.kizuna.cast.api.dto.CastUpdateRequest;
import com.kizuna.cast.domain.AttendanceReferenceCheck;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastEnrollmentStatus;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionRepository;
import com.kizuna.cast.domain.CastInvitationStatus;
import com.kizuna.cast.domain.CastManagementView;
import com.kizuna.cast.domain.CastProfile;
import com.kizuna.cast.domain.CastProfileRepository;
import com.kizuna.cast.domain.CastPublicationStatus;
import com.kizuna.cast.domain.OrderReferenceCheck;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CastService {

  /** カスタムフィールド値の最大文字数。 */
  static final int MAX_VALUE_LENGTH = 500;

  private static final Set<String> PROFILE_SORT_FIELDS =
      Set.of(
          "name",
          "photoUrl",
          "introduction",
          "age",
          "height",
          "bust",
          "waist",
          "hip",
          "displayOrder",
          "publicationStatus");

  private final CastEnrollmentRepository castRepository;
  private final CastEnrollmentService enrollmentService;
  private final CastMapper castMapper;
  private final CastProfileRepository profileRepository;
  private final StoreRepository storeRepository;
  private final StoreContext storeContext;
  private final CastInvitationService castInvitationService;
  private final CastFieldDefinitionRepository castFieldDefinitionRepository;
  private final AttendanceReferenceCheck attendanceReferenceCheck;
  private final OrderReferenceCheck orderReferenceCheck;
  private final BusinessAudit audit;

  @StoreScoped
  @Transactional(readOnly = true)
  public Page<CastSummaryResponse> list(String search, Pageable pageable) {
    Sort requestedSort = pageable.getSort();
    if (requestedSort.getOrderFor("id") == null)
      requestedSort = requestedSort.and(Sort.by(CastEnrollment::getId));
    Sort sort =
        Sort.by(
            requestedSort.stream()
                .map(
                    order -> {
                      String alias =
                          PROFILE_SORT_FIELDS.contains(order.getProperty()) ? "p." : "e.";
                      return order.withProperty(alias + order.getProperty());
                    })
                .toList());
    Page<CastManagementView> page =
        profileRepository.search(
            pattern(search),
            PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort));
    Map<String, CastInvitationStatus> statuses =
        castInvitationService.deriveStatuses(
            page.getContent().stream().map(CastManagementView::enrollment).toList());
    Set<String> deletable =
        deletableIds(page.getContent().stream().map(CastManagementView::enrollment).toList());
    return page.map(
        view ->
            castMapper.toSummaryResponse(
                view.enrollment(),
                view.profile(),
                statuses.get(view.enrollment().getId()),
                deletable.contains(view.enrollment().getId())));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CastResponse get(String id) {
    CastEnrollment enrollment = requireEnrollment(id);
    return castMapper.toResponse(
        enrollment,
        requireProfile(id),
        castInvitationService.deriveStatuses(List.of(enrollment)).get(id),
        deletableIds(List.of(enrollment)).contains(id));
  }

  private Set<String> deletableIds(List<CastEnrollment> enrollments) {
    Set<String> candidates =
        enrollments.stream()
            .filter(CastEnrollment::isDeletable)
            .map(CastEnrollment::getId)
            .collect(Collectors.toCollection(HashSet::new));
    if (candidates.isEmpty()) return candidates;
    Set<String> ids = Set.copyOf(candidates);
    Set<String> referenced = new HashSet<>(attendanceReferenceCheck.findReferencedCastIds(ids));
    referenced.addAll(orderReferenceCheck.findReferencedCastIds(ids));
    candidates.removeAll(referenced);
    return candidates;
  }

  private CastEnrollment requireEnrollment(String id) {
    return castRepository.findById(id).orElseThrow(() -> new NotFoundException("キャストが見つかりません"));
  }

  private CastProfile requireProfile(String id) {
    return profileRepository
        .findByEnrollmentId(id)
        .orElseThrow(() -> new NotFoundException("プロフィールが見つかりません"));
  }

  public static String pattern(String search) {
    return "%"
        + (search == null ? "" : search.trim())
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        + "%";
  }

  /** 実績の参照先が現在店舗に属することを、削除と直列化して確認する。在籍の後にシフトをロックする。 */
  @StoreScoped
  @Transactional
  public boolean existsForCurrentStoreForUpdate(String id) {
    return castRepository.findScopedByIdForUpdate(id).isPresent();
  }

  @StoreScoped
  @Transactional
  public CastResponse create(CastCreateRequest request, String actorEmail) {
    storeRepository.lockCastFields(storeContext.getStoreId());
    CastEnrollment enrollment =
        castRepository.save(
            CastEnrollment.builder()
                .status(
                    request.getStatus() == null
                        ? CastEnrollmentStatus.ENROLLED
                        : CastEnrollmentStatus.valueOf(request.getStatus()))
                .build());
    enrollmentService.recordCreation(enrollment, actorEmail);
    CastProfile profile = profileRepository.save(castMapper.toProfile(request, enrollment.getId()));
    profileRepository.flush();
    audit.record(
        actorEmail,
        enrollment.getStoreId(),
        "CAST_ENROLLMENT_CREATED",
        "CAST_ENROLLMENT",
        enrollment.getId(),
        null,
        null,
        Map.of(),
        snapshot(enrollment, profile));
    return castMapper.toResponse(
        enrollment, profile, null, deletableIds(List.of(enrollment)).contains(enrollment.getId()));
  }

  private static Map<String, String> snapshot(CastEnrollment enrollment, CastProfile profile) {
    return Map.of(
        "exists", "true",
        "cast_id", Objects.toString(enrollment.getCastId(), ""),
        "status", enrollment.getStatus().name(),
        "ended_at", Objects.toString(enrollment.getEndedAt(), ""),
        "enrollment_version", Objects.toString(enrollment.getVersion(), ""),
        "profile_id", Objects.toString(profile.getId(), ""),
        "profile_version", Objects.toString(profile.getVersion(), ""),
        "publication_status", profile.getPublicationStatus().name());
  }

  @StoreScoped
  @Transactional
  public CastResponse update(String id, CastUpdateRequest request, String actorEmail) {
    storeRepository.lockCastFields(storeContext.getStoreId());
    CastEnrollment enrollment =
        castRepository
            .findScopedByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundException("キャストが見つかりません"));
    CastProfile profile = requireProfile(id);
    var before = snapshot(enrollment, profile);
    var privateBefore = privateValues(enrollment, profile);
    if (request.getName() != null && request.getName().isBlank())
      throw new ServiceException("源氏名は必須です");
    if (request.getCustomFields() != null) {
      validateCustomFields(request.getCustomFields());
      Map<String, String> internal = new HashMap<>();
      Map<String, String> external = new HashMap<>();
      for (CastFieldDefinition definition :
          castFieldDefinitionRepository.findAllByOrderByDisplayOrderAsc()) {
        if (request.getCustomFields().containsKey(definition.getKey())) {
          (Boolean.TRUE.equals(definition.getIsPublic()) ? external : internal)
              .put(definition.getKey(), request.getCustomFields().get(definition.getKey()));
        }
      }
      enrollmentService.replaceInternalFields(enrollment, internal, actorEmail);
      profile.replaceCustomFields(external);
    }
    profile.apply(castMapper.toPatch(request));
    var changedFields = new TreeSet<String>();
    var privateAfter = privateValues(enrollment, profile);
    privateAfter.forEach(
        (key, value) -> {
          if (!Objects.equals(privateBefore.get(key), value)) changedFields.add(key);
        });
    boolean changed = !before.equals(snapshot(enrollment, profile)) || !changedFields.isEmpty();
    profileRepository.flush();
    if (changed) {
      var after = new HashMap<>(snapshot(enrollment, profile));
      if (!changedFields.isEmpty())
        after.put("redacted_fields_changed", String.join(",", changedFields));
      audit.record(
          actorEmail,
          enrollment.getStoreId(),
          "CAST_ENROLLMENT_UPDATED",
          "CAST_ENROLLMENT",
          enrollment.getId(),
          null,
          null,
          before,
          after);
    }
    return castMapper.toResponse(
        enrollment, profile, null, deletableIds(List.of(enrollment)).contains(enrollment.getId()));
  }

  private static Map<String, Object> privateValues(CastEnrollment enrollment, CastProfile profile) {
    var values = new HashMap<String, Object>();
    values.put("name", profile.getName());
    values.put("photo_url", profile.getPhotoUrl());
    values.put("introduction", profile.getIntroduction());
    values.put("age", profile.getAge());
    values.put("height", profile.getHeight());
    values.put("bust", profile.getBust());
    values.put("waist", profile.getWaist());
    values.put("hip", profile.getHip());
    values.put("display_order", profile.getDisplayOrder());
    values.put("internal_custom_fields", new HashMap<>(enrollment.getCustomFields()));
    values.put("public_custom_fields", new HashMap<>(profile.getCustomFields()));
    return values;
  }

  @StoreScoped
  @Transactional
  public CastPublicationResponse changePublication(String id, CastPublicationStatus status) {
    requireEnrollment(id);
    CastProfile profile = requireProfile(id);
    var before = profileSnapshot(profile);
    profile.changePublication(status);
    boolean changed = !before.equals(profileSnapshot(profile));
    profileRepository.flush();
    if (changed) {
      audit.record(
          SecurityContextHolder.getContext().getAuthentication().getName(),
          profile.getStoreId(),
          "CAST_PROFILE_PUBLICATION_CHANGED",
          "CAST_PROFILE",
          profile.getId(),
          "CAST_ENROLLMENT",
          id,
          before,
          profileSnapshot(profile));
    }
    return new CastPublicationResponse(profile.getPublicationStatus());
  }

  private static Map<String, String> profileSnapshot(CastProfile profile) {
    return Map.of(
        "enrollment_id", profile.getEnrollmentId(),
        "version", Objects.toString(profile.getVersion(), ""),
        "publication_status", profile.getPublicationStatus().name());
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

  /** 本人未紐づけの草稿のみ削除できる。受注・実績の参照は履歴の根拠なので維持する。 */
  @StoreScoped
  @Transactional
  public void delete(String id) {
    CastEnrollment enrollment =
        castRepository
            .findScopedByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundException("キャストが見つかりません"));
    if (!enrollment.isDeletable()) {
      throw new ConflictException("本人紐づけ済み・退店済みの在籍は削除できません");
    }
    if (attendanceReferenceCheck.findReferencedCastIds(List.of(id)).contains(id)) {
      throw attendanceReferenced();
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
              () -> new ConflictException("受注が紐づいているキャストは削除できません。退店操作を利用してください"),
              DbConstraint.FK_T_ORDER_APPLICATIONS_CAST,
              () -> new ConflictException("予約申請が紐づいているキャストは削除できません。退店操作を利用してください"),
              DbConstraint.FK_T_ATTENDANCES_CAST,
              CastService::attendanceReferenced,
              DbConstraint.FK_T_ATTENDANCES_SHIFT,
              CastService::attendanceReferenced));
    }
  }

  /**
   * 実績に参照されるキャストは削除できない、の断り。
   *
   * <p>前置の判定と外部キー違反の写像が同じ文言を返すのは、両者が同じ拒否であって利用者の次の一手が変わらないため。
   * 判定を擦り抜けられるのは、判定と削除の間に実績が記録された並行の場合だけである — シフトへの連鎖があるので、そのとき鳴る外部キーはキャスト側とは限らない。
   */
  private static ConflictException attendanceReferenced() {
    return new ConflictException("実績が記録されているキャストは削除できません。退店操作を利用してください");
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public List<CastPublicResponse> listPublished() {
    List<CastFieldDefinition> publicDefinitions =
        castFieldDefinitionRepository.findByIsPublicTrueOrderByDisplayOrderAsc();
    return profileRepository.findPublished().stream()
        .map(cast -> castMapper.toPublicResponse(cast, publicDefinitions))
        .toList();
  }
}
