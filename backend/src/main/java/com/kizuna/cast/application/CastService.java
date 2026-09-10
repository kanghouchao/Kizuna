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
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.store.domain.StoreRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

  @StoreScoped
  @Transactional(readOnly = true)
  public Page<CastSummaryResponse> list(String search, Pageable pageable) {
    Sort sort =
        Sort.by(
            pageable.getSort().stream()
                .map(
                    order -> {
                      String alias =
                          PROFILE_SORT_FIELDS.contains(order.getProperty()) ? "p." : "e.";
                      return order.withProperty(alias + order.getProperty());
                    })
                .toList());
    if (sort.getOrderFor("e.id") == null) sort = sort.and(Sort.by("e.id"));
    Page<CastManagementView> page =
        profileRepository.search(
            pattern(search),
            PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort));
    Map<String, CastInvitationStatus> statuses =
        castInvitationService.deriveStatuses(
            page.getContent().stream().map(CastManagementView::enrollment).toList());
    return page.map(
        view ->
            castMapper.toSummaryResponse(
                view.enrollment(), view.profile(), statuses.get(view.enrollment().getId())));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CastResponse get(String id) {
    CastEnrollment enrollment = requireEnrollment(id);
    return castMapper.toResponse(
        enrollment,
        requireProfile(id),
        castInvitationService.deriveStatuses(List.of(enrollment)).get(id));
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

  /**
   * 指定 id のキャストが現在店舗に属するか判定する（他モジュールからの帰属チェック用ポート）。 storeFilter が効くため、他店舗のキャストは存在しないものとして false
   * を返す。
   */
  @StoreScoped
  @Transactional(readOnly = true)
  public boolean existsForCurrentStore(String id) {
    return castRepository.findById(id).isPresent();
  }

  /**
   * {@link #existsForCurrentStore} と同じ判定を、そのキャストを指す行を建てる間だけ押さえて行う。
   *
   * <p>ロック順序の契約（キャスト → シフト）は {@link CastEnrollmentRepository#findScopedByIdForUpdate} に記す。キャストと
   * シフトを同時に指す行を建てる操作は、この口を通ってからシフトを押さえる。
   */
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
    return castMapper.toResponse(enrollment, profile, null);
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
    return castMapper.toResponse(enrollment, profile, null);
  }

  @StoreScoped
  @Transactional
  public CastPublicationResponse changePublication(String id, CastPublicationStatus status) {
    requireEnrollment(id);
    CastProfile profile = requireProfile(id);
    profile.changePublication(status);
    return new CastPublicationResponse(profile.getPublicationStatus());
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
    if (attendanceReferenceCheck.existsForCast(id)) {
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
