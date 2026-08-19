package com.kizuna.shift.application;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
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
import com.kizuna.shift.domain.ShiftRequestType;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
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
  private final PlatformUserRepository platformUserRepository;

  /**
   * <b>系列は 1 つの断面で読む</b>（{@code REPEATABLE READ}）。シフト・申請・実績・実行主体と 4 回問い合わせるので、 既定の READ COMMITTED
   * では文ごとに断面を取り直し、間に変更申請の承認が commit されると同じ応答が「承認済みの 変更申請」と「変更前のシフト」を同時に載せる — 系列を 1
   * 本の正本として見せるという読み口の目的が崩れる。 断面を固定して根を断つのは {@code CustomerService} / {@code OrderService}
   * の群読み口と同じ選択である。
   */
  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ShiftDetailResponse detail(String id) {
    Shift shift =
        shiftRepository.findById(id).orElseThrow(() -> new NotFoundException("シフトが見つかりません: " + id));
    ShiftRequest origin =
        shiftRequestRepository
            .findFirstByShiftIdAndTypeOrderByCreatedAtAscIdAsc(id, ShiftRequestType.NEW)
            .orElse(null);
    Attendance attendance = attendanceRepository.findByShiftIdAndCancelledAtIsNull(id).orElse(null);

    Map<Long, String> actorNames =
        resolveActorNames(shift, origin == null ? List.of() : List.of(origin), attendance);
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
        .origin(origin == null ? null : toLineage(origin, actorNames))
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
   * シフトの変更申請履歴。続きはカーソルで辿る。
   *
   * <p>詳細へ埋めないのは、提出のたびに増え続け 1 本のシフトに対する上限も一意性の守衛も無いためである （api-guidelines §5）。出生の NEW だけが構造的に高々 1
   * 本なので詳細に残る。
   *
   * <p>断面を固定するのは詳細と同じ理由による。履歴と実行主体を 2 回に分けて問い合わせるので、間に承認が commit されると同じ応答の中で申請の状態と実行主体が食い違う。
   */
  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CursorPage<ShiftRequestLineageResponse> changeRequests(
      String shiftId, String cursor, int requestedSize) {
    // シフトの存在と作用域はここで確かめる。履歴だけを問い合わせると、他店舗のシフト id に対して
    // 「空の履歴」を返してしまい、存在しないことと区別が付かなくなる。
    if (!shiftRepository.existsById(shiftId)) {
      throw new NotFoundException("シフトが見つかりません: " + shiftId);
    }
    int size = CursorPage.clampSize(requestedSize);
    // 続きの有無は上限より 1 件多く取って判る。総件数の問い合わせを毎回撒かずに済む。
    Limit limit = Limit.of(size + 1);
    List<ShiftRequest> fetched =
        cursor == null
            ? shiftRequestRepository.findChangeHistoryByShiftId(shiftId, limit)
            : fetchChangeHistoryAfter(shiftId, PageCursor.decode(cursor), limit);
    CursorPage<ShiftRequest> page = CursorPage.of(fetched, size, ShiftLineageService::cursorOf);
    Map<Long, String> actorNames = resolveProcessorNames(page.content());
    return page.map(request -> toLineage(request, actorNames));
  }

  private List<ShiftRequest> fetchChangeHistoryAfter(
      String shiftId, PageCursor cursor, Limit limit) {
    return shiftRequestRepository.findChangeHistoryByShiftIdAfter(
        shiftId, cursor.timestampKey(), cursor.id(), limit);
  }

  /** 続きの位置は履歴の並び（提出時刻 + id）と同じ組で作る。組が並びとずれると、続きが手前へ戻るか行を飛ばす。 */
  private static String cursorOf(ShiftRequest request) {
    return new PageCursor(request.getCreatedAt().toString(), request.getId()).encode();
  }

  /** 履歴 1 ページ分の承認・却下の実行者を一度に引く。 */
  private Map<Long, String> resolveProcessorNames(List<ShiftRequest> requests) {
    Set<Long> ids = new HashSet<>();
    requests.forEach(request -> ids.add(request.getProcessedBy()));
    ids.remove(null);
    return namesOf(ids);
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
    return namesOf(ids);
  }

  private Map<Long, String> namesOf(Set<Long> actorIds) {
    if (actorIds.isEmpty()) {
      return Map.of();
    }
    return platformUserRepository.findAllById(new ArrayList<>(actorIds)).stream()
        .collect(Collectors.toMap(PlatformUser::getId, PlatformUser::getDisplayName));
  }

  /** 実行主体の欄。利用者が削除された行は id ごと欠ける（FK が SET NULL）ので、節点そのものが null になる。 */
  private static ActorResponse actor(Long id, Map<Long, String> actorNames) {
    return id == null ? null : ActorResponse.builder().id(id).name(actorNames.get(id)).build();
  }
}
