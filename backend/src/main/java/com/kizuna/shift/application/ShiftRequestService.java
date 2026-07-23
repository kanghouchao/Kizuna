package com.kizuna.shift.application;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shift.api.dto.ShiftRequestMapper;
import com.kizuna.shift.api.dto.StoreShiftRequestResponse;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftRequestRepository;
import com.kizuna.shift.domain.ShiftRequestStatus;
import java.util.List;
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
    return requests.stream().map(shiftRequestMapper::toStoreResponse).toList();
  }

  /**
   * 出勤希望を承認する。request の (cast_id, store_id, work_date, start_time, end_time) を原様に CONFIRMED Shift
   * として新規作成する（同一トランザクション）。時刻調整は承認後の既存シフト編集で行う。
   */
  @StoreScoped
  @Transactional
  public StoreShiftRequestResponse approve(String id) {
    ShiftRequest request = findOwnRequest(id);
    request.approve();

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
        .orElseThrow(() -> new ServiceException("出勤希望が見つかりません: " + id));
  }

  private ShiftRequestStatus parseStatus(String status) {
    try {
      return ShiftRequestStatus.valueOf(status);
    } catch (IllegalArgumentException ex) {
      throw new ServiceException("不正なステータスです: " + status);
    }
  }
}
