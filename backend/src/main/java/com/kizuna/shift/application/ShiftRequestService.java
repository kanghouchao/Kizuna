package com.kizuna.shift.application;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shift.api.dto.ShiftRequestMapper;
import com.kizuna.shift.api.dto.StoreShiftRequestResponse;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftRequestKind;
import com.kizuna.shift.domain.ShiftRequestRepository;
import com.kizuna.shift.domain.ShiftRequestStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 店舗側の出勤希望ユースケース（inbox 閲覧・承認・却下）。 */
@Service
@RequiredArgsConstructor
public class ShiftRequestService {

  private final ShiftRequestRepository shiftRequestRepository;
  private final ShiftRepository shiftRepository;
  private final ShiftRequestMapper shiftRequestMapper;

  @StoreScoped
  @Transactional(readOnly = true)
  public List<StoreShiftRequestResponse> list(String status) {
    List<ShiftRequest> requests =
        status == null
            ? shiftRequestRepository.findAllByOrderByCreatedAtAsc()
            : shiftRequestRepository.findByStatusOrderByCreatedAtAsc(parseStatus(status));
    Map<String, Shift> targets = loadTargetShifts(requests);
    return requests.stream().map(request -> toStoreResponse(request, targets)).toList();
  }

  /**
   * 出勤希望を承認する。
   *
   * <p>新規希望（NEW）は request の (cast_id, store_id, work_date, start_time, end_time) を原様に CONFIRMED
   * Shift として新規作成する（同一トランザクション）。時刻調整は承認後の既存シフト編集で行う。
   *
   * <p>変更申請（CHANGE）は対象シフトの行をその場で更新する。作り直しではないので、シフト側にだけ載っている設定は 申請が持つ日付・時刻の外側で保たれる（{@link
   * ShiftRequest#toShiftPatch} が構造で担保する）。
   */
  @StoreScoped
  @Transactional
  public StoreShiftRequestResponse approve(String id, String actor) {
    ShiftRequest request = findOwnRequest(id);
    request.approve(actor);

    if (request.getKind() == ShiftRequestKind.CHANGE) {
      updateTargetShift(request);
    } else {
      // store_id は StoreScopeStampListener が @PrePersist で採番する
      Shift shift =
          Shift.builder()
              .castId(request.getCastId())
              .workDate(request.getWorkDate())
              .startTime(request.getStartTime())
              .endTime(request.getEndTime())
              .status("CONFIRMED")
              .build();
      shift.markApproved(actor);
      request.linkToShift(shiftRepository.save(shift).getId());
    }

    return respond(shiftRequestRepository.save(request));
  }

  @StoreScoped
  @Transactional
  public StoreShiftRequestResponse decline(String id, String actor) {
    ShiftRequest request = findOwnRequest(id);
    request.decline(actor);
    return respond(shiftRequestRepository.save(request));
  }

  /**
   * 変更申請の対象シフトを更新する。
   *
   * <p>対象は @StoreScoped の storeFilter 下で引くが、id 検索はフィルタを迂回し得るため、店舗帰属を申請と突き合わせて明示的に 確認する —
   * 他店のシフトを指した申請 id で越境更新されないことを、フィルタの適用有無に依存せず保証する。 対象が既に削除されている場合（FK は SET
   * NULL）は更新すべき正本が無いため拒否する。
   */
  private void updateTargetShift(ShiftRequest request) {
    Shift target =
        request.getTargetShiftId() == null
            ? null
            : shiftRepository.findById(request.getTargetShiftId()).orElse(null);
    if (target == null || !request.getStoreId().equals(target.getStoreId())) {
      throw new NotFoundException("変更対象のシフトが見つかりません");
    }
    target.apply(request.toShiftPatch());
    shiftRepository.save(target);
  }

  /** 変更申請が指す対象シフトをまとめて引く（一覧で 1 件ずつ引かないため）。自店に属さないものは落とす。 */
  private Map<String, Shift> loadTargetShifts(List<ShiftRequest> requests) {
    List<String> targetIds =
        requests.stream()
            .filter(request -> request.getKind() == ShiftRequestKind.CHANGE)
            .map(ShiftRequest::getTargetShiftId)
            .filter(Objects::nonNull)
            .toList();
    if (targetIds.isEmpty()) {
      return Map.of();
    }
    return shiftRepository.findAllById(targetIds).stream()
        .collect(Collectors.toMap(Shift::getId, Function.identity()));
  }

  private StoreShiftRequestResponse respond(ShiftRequest request) {
    return toStoreResponse(request, loadTargetShifts(List.of(request)));
  }

  /** inbox 応答へ変換し、変更申請には対象シフトの現在値（変更前）を添える。 */
  private StoreShiftRequestResponse toStoreResponse(
      ShiftRequest request, Map<String, Shift> targets) {
    StoreShiftRequestResponse response = shiftRequestMapper.toStoreResponse(request);
    if (request.getKind() != ShiftRequestKind.CHANGE || request.getTargetShiftId() == null) {
      return response;
    }
    Shift target = targets.get(request.getTargetShiftId());
    if (target != null && request.getStoreId().equals(target.getStoreId())) {
      response.setCurrentWorkDate(target.getWorkDate());
      response.setCurrentStartTime(target.getStartTime());
      response.setCurrentEndTime(target.getEndTime());
    }
    return response;
  }

  private ShiftRequest findOwnRequest(String id) {
    return shiftRequestRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("出勤希望が見つかりません: " + id));
  }

  private ShiftRequestStatus parseStatus(String status) {
    try {
      return ShiftRequestStatus.valueOf(status);
    } catch (IllegalArgumentException ex) {
      throw new ServiceException("不正なステータスです: " + status);
    }
  }
}
