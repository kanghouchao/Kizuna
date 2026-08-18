package com.kizuna.shift.application;

import com.kizuna.cast.application.CastService;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shift.api.dto.AttendanceCorrectionRequest;
import com.kizuna.shift.api.dto.AttendanceCreateRequest;
import com.kizuna.shift.api.dto.AttendanceMapper;
import com.kizuna.shift.api.dto.AttendanceResponse;
import com.kizuna.shift.domain.Attendance;
import com.kizuna.shift.domain.AttendanceCorrection;
import com.kizuna.shift.domain.AttendanceCorrectionRepository;
import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 当日実績（実際に出勤した事実）の記録・訂正・取消のユースケース。
 *
 * <p>記録は店舗側（SHIFT_MANAGE）が行い、キャスト本人の打刻も事後補記の時限も無い。行の存在そのものが出勤確認で、 確認フラグや状態機械は持たない（ADR 0014）。
 *
 * <p>物理削除の口はここに無い。法定保存（労基法 109 条）の対象なので、誤建行は取消標記で導出・照会から外すだけである。
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

  /**
   * 未取消の実績の一意性（1 シフトにつき 1 行・1 営業日 1 キャストにつき 1 行）を破った書き込みの写像。
   *
   * <p>事前照会は置かない。置いても並行の敗者は結局この索引に当たるので、判定を二重に持たずに DB の一枚で受ける。
   */
  private static final Map<DbConstraint, Supplier<RuntimeException>> DUPLICATE_ACTIVE_ATTENDANCE =
      Map.of(
          DbConstraint.UQ_T_ATTENDANCES_ACTIVE_SHIFT,
          () -> new ConflictException("このシフトの実績は既に記録されています"),
          DbConstraint.UQ_T_ATTENDANCES_ACTIVE_CAST_BUSINESS_DATE,
          () -> new ConflictException("このキャストの当該営業日の実績は既に記録されています"));

  private final AttendanceRepository attendanceRepository;
  private final AttendanceCorrectionRepository correctionRepository;
  private final AttendanceMapper attendanceMapper;
  private final ShiftRepository shiftRepository;
  private final CastService castService;
  private final PlatformUserRepository platformUserRepository;
  private final BusinessDateService businessDateService;

  /** （キャスト・店舗・営業日）での照会。店舗は文脈から、取消済みは常に除外される。 */
  @StoreScoped
  @Transactional(readOnly = true)
  public List<AttendanceResponse> list(LocalDate businessDate, String castId) {
    List<Attendance> rows =
        castId == null
            ? attendanceRepository
                .findByBusinessDateAndCancelledAtIsNullOrderByActualStartAtAscIdAsc(businessDate)
            : attendanceRepository
                .findByBusinessDateAndCastIdAndCancelledAtIsNullOrderByActualStartAtAscIdAsc(
                    businessDate, castId);
    return rows.stream().map(attendanceMapper::toResponse).toList();
  }

  @StoreScoped
  @Transactional
  public AttendanceResponse record(AttendanceCreateRequest request, String actorEmail) {
    Long actorId = resolveActorId(actorEmail);
    if (!castService.existsForCurrentStore(request.getCastId())) {
      throw new NotFoundException("キャストが見つかりません: " + request.getCastId());
    }
    Attendance attendance =
        Attendance.record(
            request.getCastId(),
            resolveBusinessDate(request),
            request.getActualStartAt(),
            request.getActualEndAt(),
            request.getShiftId(),
            request.getWaitingPlace(),
            actorId);
    return attendanceMapper.toResponse(saveWithinUniqueness(attendance));
  }

  /** 記入の誤りを訂正し、同一トランザクションで編集前の姿を訂正履歴へ残す。 */
  @StoreScoped
  @Transactional
  public AttendanceResponse correct(
      String id, AttendanceCorrectionRequest request, String actorEmail) {
    Long actorId = resolveActorId(actorEmail);
    Attendance attendance = findAttendance(id);
    requireInheritedBusinessDate(attendance, request.getBusinessDate());

    correctionRepository.save(
        AttendanceCorrection.snapshotOf(attendance, actorId, OffsetDateTime.now()));
    attendance.correct(
        request.getBusinessDate(),
        request.getActualStartAt(),
        request.getActualEndAt(),
        request.getWaitingPlace(),
        actorId);
    return attendanceMapper.toResponse(saveWithinUniqueness(attendance));
  }

  /** 誤建の実績に取消標記を付ける。行は残り、導出・照会から外れるだけである。 */
  @StoreScoped
  @Transactional
  public void cancel(String id, String actorEmail) {
    Long actorId = resolveActorId(actorEmail);
    Attendance attendance = findAttendance(id);
    attendance.cancel(actorId, OffsetDateTime.now());
    attendanceRepository.save(attendance);
  }

  /** 帰属営業日を決める。優先順があり、シフトに紐づく実績はシフトの勤務日を継承する — 実開始が日付変更時刻を跨いでも 予実は同じ営業日に居る。飛び込みだけが実開始時刻から判定される。 */
  private LocalDate resolveBusinessDate(AttendanceCreateRequest request) {
    if (request.getShiftId() == null) {
      return businessDateService.businessDateOf(request.getActualStartAt());
    }
    Shift shift = findShift(request.getShiftId());
    if (!shift.getCastId().equals(request.getCastId())) {
      throw new ServiceException("シフトのキャストと実績のキャストが一致しません");
    }
    return shift.getWorkDate();
  }

  /** シフト紐づきの実績の営業日は継承であって選択ではない。訂正でずらせてしまうと予実が別の営業日へ割れる。 */
  private void requireInheritedBusinessDate(Attendance attendance, LocalDate businessDate) {
    if (attendance.getShiftId() == null) {
      return;
    }
    if (!findShift(attendance.getShiftId()).getWorkDate().equals(businessDate)) {
      throw new ServiceException("シフトに紐づく実績の営業日はシフトの勤務日と一致していなければなりません");
    }
  }

  /**
   * 一意性違反を業務例外へ写して保存する。
   *
   * <p>{@code save} ではなく {@code saveAndFlush} で書くのが要点である。flush を commit まで遅らせると、違反はこのメソッドが返った後に
   * 起き、写像は決して命中しない。
   */
  private Attendance saveWithinUniqueness(Attendance attendance) {
    try {
      return attendanceRepository.saveAndFlush(attendance);
    } catch (DataIntegrityViolationException e) {
      throw IntegrityViolations.translate(e, DUPLICATE_ACTIVE_ATTENDANCE);
    }
  }

  private Attendance findAttendance(String id) {
    return attendanceRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("実績が見つかりません: " + id));
  }

  private Shift findShift(String shiftId) {
    return shiftRepository
        .findById(shiftId)
        .orElseThrow(() -> new NotFoundException("シフトが見つかりません: " + shiftId));
  }

  private Long resolveActorId(String actorEmail) {
    return platformUserRepository
        .findByEmail(actorEmail)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
        .getId();
  }
}
