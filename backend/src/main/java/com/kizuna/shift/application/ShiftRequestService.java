package com.kizuna.shift.application;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shift.api.dto.ShiftRequestMapper;
import com.kizuna.shift.api.dto.StoreShiftRequestResponse;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftPatch;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftRequestRepository;
import com.kizuna.shift.domain.ShiftRequestStatus;
import com.kizuna.shift.domain.ShiftRequestType;
import com.kizuna.shift.domain.ShiftStatus;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 店舗側の出勤希望ユースケース（inbox 閲覧・承認・却下）。新規希望と変更申請の両種別を扱う。 */
@Service
@RequiredArgsConstructor
public class ShiftRequestService {

  private final ShiftRequestRepository shiftRequestRepository;
  private final ShiftRepository shiftRepository;
  private final ShiftRequestMapper shiftRequestMapper;
  private final PlatformUserRepository platformUserRepository;

  @StoreScoped
  @Transactional(readOnly = true)
  public List<StoreShiftRequestResponse> list(String status) {
    List<ShiftRequest> requests =
        status == null
            ? shiftRequestRepository.findAllByOrderByCreatedAtAsc()
            : shiftRequestRepository.findByStatusOrderByCreatedAtAsc(parseStatus(status));

    // 変更申請は承認判断に「現行から何がどう変わるか」が必要なため、対象シフトの現行日時を内联する。
    // 承認済みの NEW も shift_id を持つが、そちらは系列の背骨であって差分の提示先ではないので引かない。
    // storeFilter がセッション全体で有効なので、参照できるシフトは現店舗のものに限られる。
    Map<String, Shift> targetShifts =
        shiftRepository
            .findAllById(
                requests.stream()
                    .filter(request -> request.getType() == ShiftRequestType.CHANGE)
                    .map(ShiftRequest::getShiftId)
                    .filter(Objects::nonNull)
                    .toList())
            .stream()
            .collect(Collectors.toMap(Shift::getId, Function.identity()));

    return requests.stream()
        .map(
            request -> {
              StoreShiftRequestResponse response = shiftRequestMapper.toStoreResponse(request);
              Shift target = targetShifts.get(request.getShiftId());
              if (target != null) {
                response.setCurrentWorkDate(target.getWorkDate());
                response.setCurrentStartTime(target.getStartTime());
                response.setCurrentEndTime(target.getEndTime());
              }
              if (request.getType() == ShiftRequestType.CHANGE) {
                response.setApprovable(changeApplicable(request, target));
              }
              return response;
            })
        .toList();
  }

  /**
   * 変更申請が今も適用可能か（対象シフトが存在し、確定済み・申請者本人・申請時点の時間帯のまま）。 approve の各守衛と同じ条件の要約で、inbox
   * が承認不能な申請に承認操作を出さないために使う。
   */
  private boolean changeApplicable(ShiftRequest request, Shift target) {
    return target != null
        && target.getStatus() == ShiftStatus.CONFIRMED
        && request.getCastId().equals(target.getCastId())
        && slotUnchanged(request, target);
  }

  /** 対象シフトの時間帯が申請時点（original_*）から変わっていないこと。 */
  private boolean slotUnchanged(ShiftRequest request, Shift target) {
    return Objects.equals(target.getWorkDate(), request.getOriginalWorkDate())
        && Objects.equals(target.getStartTime(), request.getOriginalStartTime())
        && Objects.equals(target.getEndTime(), request.getOriginalEndTime());
  }

  /**
   * 出勤希望を承認する。NEW は request の (cast_id, store_id, work_date, start_time, end_time) を原様に CONFIRMED
   * Shift として新規作成し、CHANGE は対象シフトの日時のみを申請値へ更新する（cast_id・status・公開可否
   * は保持し、承認と独立した軸を巻き込まない）。いずれも同一トランザクション。時刻調整は承認後の既存シフト編集で行う。
   *
   * @param published NEW で生まれるシフトの公開可否。null は既定の公開可。内密の出勤はここで非公開を指定して出生させる —
   *     公開状態で生まれてから隠すまでの露出窓を作らない（ADR 0015）
   */
  @StoreScoped
  @Transactional
  public StoreShiftRequestResponse approve(String id, Boolean published, String actorEmail) {
    ShiftRequest request = findOwnRequest(id);
    Long actorId = resolveActorId(actorEmail);
    request.approve(actorId, OffsetDateTime.now());

    if (request.getType() == ShiftRequestType.CHANGE) {
      // 既存シフトの公開可否は承認では動かさないため、指定を黙って捨てずに撥ねる（切替は専用の口が受ける）。
      if (published != null) {
        throw new ServiceException("変更申請の承認では公開可否を指定できません");
      }
      // 対象シフトが削除されると FK（SET NULL）で参照が落ちる。申請は履歴として残るが、適用先が無いため承認はできない。
      if (request.getShiftId() == null) {
        throw new ServiceException("対象のシフトは既に削除されています");
      }
      Shift target =
          shiftRepository
              .findById(request.getShiftId())
              .orElseThrow(() -> new NotFoundException("シフトが見つかりません: " + request.getShiftId()));
      // 確定済み・申請者本人・申請時点のままのシフトであることは提出時だけでなく適用時にも要求する
      // （提出後に未確定へ編集された・別キャストへ付け替えられた・時間帯を変更されたシフトを上書きしない）。
      if (target.getStatus() != ShiftStatus.CONFIRMED) {
        throw new ServiceException("確定済みでないシフトには変更を適用できません");
      }
      if (!request.getCastId().equals(target.getCastId())) {
        throw new ServiceException("対象のシフトは別のキャストに変更されています");
      }
      if (!slotUnchanged(request, target)) {
        throw new ServiceException("対象のシフトは申請後に変更されています");
      }
      target.apply(
          new ShiftPatch(
              null, request.getWorkDate(), request.getStartTime(), request.getEndTime(), null));
      target.stampUpdatedBy(actorId);
      shiftRepository.save(target);
    } else {
      // store_id は StoreScopeStampListener が @PrePersist で採番する
      Shift shift =
          Shift.builder()
              .castId(request.getCastId())
              .workDate(request.getWorkDate())
              .startTime(request.getStartTime())
              .endTime(request.getEndTime())
              .status(ShiftStatus.CONFIRMED)
              .published(published == null || published)
              .createdBy(actorId)
              .build();
      // 生成したシフトを申請行へ結び、希望→確定の一跳を辿れるようにする（系列の背骨）。
      request.linkShift(shiftRepository.save(shift).getId());
    }

    return shiftRequestMapper.toStoreResponse(shiftRequestRepository.save(request));
  }

  @StoreScoped
  @Transactional
  public StoreShiftRequestResponse decline(String id, String actorEmail) {
    ShiftRequest request = findOwnRequest(id);
    request.decline(resolveActorId(actorEmail), OffsetDateTime.now());
    return shiftRequestMapper.toStoreResponse(shiftRequestRepository.save(request));
  }

  private ShiftRequest findOwnRequest(String id) {
    return shiftRequestRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("出勤希望が見つかりません: " + id));
  }

  private Long resolveActorId(String actorEmail) {
    return platformUserRepository
        .findByEmail(actorEmail)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
        .getId();
  }

  private ShiftRequestStatus parseStatus(String status) {
    try {
      return ShiftRequestStatus.valueOf(status);
    } catch (IllegalArgumentException ex) {
      throw new ServiceException("不正なステータスです: " + status);
    }
  }
}
