package com.kizuna.shift.application;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shift.application.ShiftViews.Actor;
import com.kizuna.shift.application.ShiftViews.AttendanceLineage;
import com.kizuna.shift.application.ShiftViews.RequestLineage;
import com.kizuna.shift.application.ShiftViews.ShiftDetail;
import com.kizuna.shift.domain.Attendance;
import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftRequestRepository;
import com.kizuna.shift.domain.ShiftRequestType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
  private final UserRepository userRepository;

  /**
   * <b>系列は 1 つの断面で読む</b>（{@code REPEATABLE READ}）。シフト・申請・実績・実行主体と 4 回問い合わせるので、 既定の READ COMMITTED
   * では文ごとに断面を取り直し、間に変更申請の承認が commit されると同じ応答が「承認済みの 変更申請」と「変更前のシフト」を同時に載せる — 系列を 1
   * 本の正本として見せるという読み口の目的が崩れる。 断面を固定して根を断つのは {@code CustomerService} / {@code OrderQueryService}
   * の群読み口と同じ選択である。
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ShiftDetail detail(Long storeId, String id) {
    Shift shift =
        shiftRepository
            .findByIdAndStoreId(id, storeId)
            .orElseThrow(() -> new NotFoundException("シフトが見つかりません: " + id));
    ShiftRequest origin =
        shiftRequestRepository
            .findFirstByStoreIdAndShiftIdAndTypeOrderByCreatedAtAscIdAsc(
                storeId, id, ShiftRequestType.NEW)
            .orElse(null);
    Attendance attendance =
        attendanceRepository.findByStoreIdAndShiftIdAndCancelledAtIsNull(storeId, id).orElse(null);

    Map<Long, String> actorNames =
        resolveActorNames(shift, origin == null ? List.of() : List.of(origin), attendance);
    return new ShiftDetail(
        shift.getId(),
        shift.getCastId(),
        shift.getWorkDate(),
        shift.getStartTime(),
        shift.getEndTime(),
        shift.getStatus().name(),
        shift.isPublished(),
        actor(shift.getCreatedBy(), actorNames),
        actor(shift.getUpdatedBy(), actorNames),
        shift.getCreatedAt(),
        shift.getUpdatedAt(),
        origin == null ? null : toLineage(origin, actorNames),
        attendance == null ? null : toLineage(attendance, actorNames));
  }

  private static RequestLineage toLineage(ShiftRequest request, Map<Long, String> actorNames) {
    return new RequestLineage(
        request.getId(),
        request.getType().name(),
        request.getStatus().name(),
        request.getCastId(),
        request.getWorkDate(),
        request.getStartTime(),
        request.getEndTime(),
        request.getNote(),
        request.getOriginalWorkDate(),
        request.getOriginalStartTime(),
        request.getOriginalEndTime(),
        request.getCreatedAt(),
        actor(request.getProcessedBy(), actorNames),
        request.getProcessedAt());
  }

  private static AttendanceLineage toLineage(Attendance attendance, Map<Long, String> actorNames) {
    return new AttendanceLineage(
        attendance.getId(),
        attendance.getCastId(),
        attendance.getBusinessDate(),
        attendance.getActualStartAt(),
        attendance.getActualEndAt(),
        attendance.getWaitingPlace(),
        actor(attendance.getCreatedBy(), actorNames),
        actor(attendance.getUpdatedBy(), actorNames),
        attendance.getCreatedAt(),
        attendance.getUpdatedAt());
  }

  /** 変更申請の履歴を 1 ページ分返す。 履歴と実行主体を別々に取得するため、途中の承認で同じ応答内の状態が食い違わないよう断面を固定する。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<RequestLineage> changeRequests(Long storeId, String shiftId, Pageable pageable) {
    // シフトの存在と作用域はここで確かめる。履歴だけを問い合わせると、他店舗のシフト id に対して
    // 「空の履歴」を返してしまい、存在しないことと区別が付かなくなる。
    if (!shiftRepository.existsByIdAndStoreId(shiftId, storeId)) {
      throw new NotFoundException("シフトが見つかりません: " + shiftId);
    }
    Page<ShiftRequest> page =
        shiftRequestRepository.findByStoreIdAndShiftIdAndType(
            storeId, shiftId, ShiftRequestType.CHANGE, pageable);
    Map<Long, String> actorNames = resolveProcessorNames(page.getContent());
    return page.map(request -> toLineage(request, actorNames));
  }

  /** 履歴 1 ページ分の承認・却下の実行者を一度に引く。 */
  private Map<Long, String> resolveProcessorNames(List<ShiftRequest> requests) {
    Set<Long> ids = new HashSet<>();
    requests.forEach(request -> ids.add(request.getProcessedBy()));
    ids.remove(null);
    return namesOf(ids);
  }

  /**
   * 系列に現れる実行主体の表示名を一度に引く。行に残るのは id だけで、店舗側の呼び手には利用者を引く読み口が 無い（{@code /staff}
   * はグループ級）ため、ここで解決しないと「実行主体が辿れる」が数値で止まる。
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
    return namesOf(ids);
  }

  private Map<Long, String> namesOf(Set<Long> actorIds) {
    if (actorIds.isEmpty()) {
      return Map.of();
    }
    return userRepository.findAllById(new ArrayList<>(actorIds)).stream()
        .collect(Collectors.toMap(User::getId, User::getDisplayName));
  }

  /** 実行主体の欄。利用者が削除された行は id ごと欠ける（FK が SET NULL）ので、節点そのものが null になる。 */
  private static Actor actor(Long id, Map<Long, String> actorNames) {
    return id == null ? null : new Actor(id, actorNames.get(id));
  }
}
