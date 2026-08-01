package com.kizuna.shift.application;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
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

  @StoreScoped
  @Transactional(readOnly = true)
  public List<StoreShiftRequestResponse> list(String status) {
    List<ShiftRequest> requests =
        status == null
            ? shiftRequestRepository.findAllByOrderByCreatedAtAsc()
            : shiftRequestRepository.findByStatusOrderByCreatedAtAsc(parseStatus(status));

    // 変更申請は承認判断に「現行から何がどう変わるか」が必要なため、対象シフトの現行日時を内联する。
    // storeFilter がセッション全体で有効なので、参照できるシフトは現店舗のものに限られる。
    Map<String, Shift> targetShifts =
        shiftRepository
            .findAllById(
                requests.stream().map(ShiftRequest::getShiftId).filter(Objects::nonNull).toList())
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
              return response;
            })
        .toList();
  }

  /**
   * 出勤希望を承認する。NEW は request の (cast_id, store_id, work_date, start_time, end_time) を原様に CONFIRMED
   * Shift として新規作成し、CHANGE は対象シフトの日時のみを申請値へ更新する（cast_id・status
   * は保持し、承認と独立した軸を巻き込まない）。いずれも同一トランザクション。時刻調整は承認後の既存シフト編集で行う。
   */
  @StoreScoped
  @Transactional
  public StoreShiftRequestResponse approve(String id) {
    ShiftRequest request = findOwnRequest(id);
    request.approve();

    if (request.getType() == ShiftRequestType.CHANGE) {
      Shift target =
          shiftRepository
              .findById(request.getShiftId())
              .orElseThrow(() -> new NotFoundException("シフトが見つかりません: " + request.getShiftId()));
      target.apply(
          new ShiftPatch(
              null, request.getWorkDate(), request.getStartTime(), request.getEndTime(), null));
      shiftRepository.save(target);
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
      shiftRepository.save(shift);
    }

    return shiftRequestMapper.toStoreResponse(shiftRequestRepository.save(request));
  }

  @StoreScoped
  @Transactional
  public StoreShiftRequestResponse decline(String id) {
    ShiftRequest request = findOwnRequest(id);
    request.decline();
    return shiftRequestMapper.toStoreResponse(shiftRequestRepository.save(request));
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
