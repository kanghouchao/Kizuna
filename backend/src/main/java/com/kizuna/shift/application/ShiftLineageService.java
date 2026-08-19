package com.kizuna.shift.application;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shift.api.dto.ActorResponse;
import com.kizuna.shift.api.dto.AttendanceLineageResponse;
import com.kizuna.shift.api.dto.ShiftDetailResponse;
import com.kizuna.shift.api.dto.ShiftRequestLineageResponse;
import com.kizuna.shift.domain.Attendance;
import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftRequestRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * シフト 1 本を起点に系列（希望・変更申請・当日実績）を辿る読み口。背骨は申請側の shift_id で、シフトは申請を 指し返さないため、辿れるのは「シフトが存在する間」に限られる（ADR
 * 0014）。
 *
 * <p>実績は未取消の 1 行だけを載せる。ここだけ全史を出すと「取消済みはどの読み口にも現れない」という ADR 0014 の前提が片面だけ崩れる。
 */
@Service
@RequiredArgsConstructor
public class ShiftLineageService {

  private final ShiftRepository shiftRepository;
  private final ShiftRequestRepository shiftRequestRepository;
  private final AttendanceRepository attendanceRepository;
  private final PlatformUserRepository platformUserRepository;

  @StoreScoped
  @Transactional(readOnly = true)
  public ShiftDetailResponse detail(String id) {
    Shift shift =
        shiftRepository.findById(id).orElseThrow(() -> new NotFoundException("シフトが見つかりません: " + id));
    List<ShiftRequest> requests = shiftRequestRepository.findByShiftIdOrderByCreatedAtAscIdAsc(id);
    Attendance attendance = attendanceRepository.findByShiftIdAndCancelledAtIsNull(id).orElse(null);

    Map<Long, String> actorNames = resolveActorNames(shift, requests, attendance);
    return ShiftDetailResponse.builder()
        .id(shift.getId())
        .castId(shift.getCastId())
        .workDate(shift.getWorkDate())
        .startTime(shift.getStartTime())
        .endTime(shift.getEndTime())
        .status(shift.getStatus().name())
        .published(shift.isPublished())
        .createdBy(actor(shift.getCreatedBy(), actorNames))
        .updatedBy(actor(shift.getUpdatedBy(), actorNames))
        .createdAt(shift.getCreatedAt())
        .updatedAt(shift.getUpdatedAt())
        .requests(requests.stream().map(request -> toLineage(request, actorNames)).toList())
        .attendance(attendance == null ? null : toLineage(attendance, actorNames))
        .build();
  }

  private static ShiftRequestLineageResponse toLineage(
      ShiftRequest request, Map<Long, String> actorNames) {
    return ShiftRequestLineageResponse.builder()
        .id(request.getId())
        .type(request.getType().name())
        .status(request.getStatus().name())
        .castId(request.getCastId())
        .workDate(request.getWorkDate())
        .startTime(request.getStartTime())
        .endTime(request.getEndTime())
        .note(request.getNote())
        .originalWorkDate(request.getOriginalWorkDate())
        .originalStartTime(request.getOriginalStartTime())
        .originalEndTime(request.getOriginalEndTime())
        .createdAt(request.getCreatedAt())
        .processedBy(actor(request.getProcessedBy(), actorNames))
        .processedAt(request.getProcessedAt())
        .build();
  }

  private static AttendanceLineageResponse toLineage(
      Attendance attendance, Map<Long, String> actorNames) {
    return AttendanceLineageResponse.builder()
        .id(attendance.getId())
        .castId(attendance.getCastId())
        .businessDate(attendance.getBusinessDate())
        .actualStartAt(attendance.getActualStartAt())
        .actualEndAt(attendance.getActualEndAt())
        .waitingPlace(attendance.getWaitingPlace())
        .createdBy(actor(attendance.getCreatedBy(), actorNames))
        .updatedBy(actor(attendance.getUpdatedBy(), actorNames))
        .createdAt(attendance.getCreatedAt())
        .updatedAt(attendance.getUpdatedAt())
        .build();
  }

  /**
   * 系列に現れる実行主体の表示名を一度に引く。行に残るのは id だけで、店舗側の呼び手には利用者を引く読み口が 無い（{@code /platform/staff}
   * は平台面）ため、ここで解決しないと「実行主体が辿れる」が数値で止まる。
   */
  private Map<Long, String> resolveActorNames(
      Shift shift, List<ShiftRequest> requests, Attendance attendance) {
    Set<Long> ids = new HashSet<>();
    ids.add(shift.getCreatedBy());
    ids.add(shift.getUpdatedBy());
    requests.forEach(request -> ids.add(request.getProcessedBy()));
    if (attendance != null) {
      ids.add(attendance.getCreatedBy());
      ids.add(attendance.getUpdatedBy());
    }
    ids.remove(null);
    if (ids.isEmpty()) {
      return Map.of();
    }
    return platformUserRepository.findAllById(new ArrayList<>(ids)).stream()
        .collect(Collectors.toMap(PlatformUser::getId, PlatformUser::getDisplayName));
  }

  /** 実行主体の欄。利用者が削除された行は id ごと欠ける（FK が SET NULL）ので、節点そのものが null になる。 */
  private static ActorResponse actor(Long id, Map<Long, String> actorNames) {
    return id == null ? null : ActorResponse.builder().id(id).name(actorNames.get(id)).build();
  }
}
