package com.kizuna.shift.application;

import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastProfile;
import com.kizuna.cast.domain.CastProfileRepository;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shift.application.ShiftCommand.CreateShift;
import com.kizuna.shift.application.ShiftViews.PublicShift;
import com.kizuna.shift.application.ShiftViews.ShiftView;
import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftPatch;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequestRepository;
import com.kizuna.shift.domain.ShiftStatus;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShiftService {

  private final ShiftRepository shiftRepository;
  private final AttendanceRepository attendanceRepository;
  private final CastEnrollmentRepository enrollments;
  private final StoreRepository storeRepository;
  private final CastProfileRepository castRepository;
  private final UserRepository userRepository;
  private final BusinessDateService businessDateService;
  private final BusinessAudit audit;
  private final ShiftRequestRepository requests;

  @Transactional(readOnly = true)
  public List<ShiftView> list(Long storeId, LocalDate from, LocalDate to) {
    return shiftRepository.findByStoreIdAndWorkDateBetween(storeId, from, to).stream()
        .map(this::toView)
        .toList();
  }

  /**
   * 公開出勤表用に「現在の営業日」の露出可能（CONFIRMED ∧ 公開可）なシフトを start_time 昇順で返す。 公開条件（PUBLISHED ∧
   * ENROLLED）を満たさないキャストのシフトは公開一覧 ({@code /casts/public}) に整合させて除外する。cast 表示情報は公開されている
   * cast.domain（{@code CastProfile}）を 直接参照して結合する（cast の応答 DTO は公開面ではないため）。t_cast_enrollments の参照は
   * {@code findPublished} の店舗条件で現店舗に絞られる。
   */
  @Transactional(readOnly = true)
  public List<PublicShift> listPublicToday(Long storeId) {
    List<Shift> shifts =
        shiftRepository.findByStoreIdAndWorkDateAndStatusAndPublishedTrueOrderByStartTimeAsc(
            storeId, businessDateService.currentBusinessDate(), ShiftStatus.CONFIRMED);
    if (shifts.isEmpty()) {
      return List.of();
    }
    Map<String, CastProfile> activeCasts =
        castRepository.findPublished(storeId).stream()
            .collect(Collectors.toMap(CastProfile::getEnrollmentId, Function.identity()));
    return shifts.stream()
        .filter(shift -> activeCasts.containsKey(shift.getCastId()))
        .map(
            shift -> {
              CastProfile cast = activeCasts.get(shift.getCastId());
              return new PublicShift(
                  shift.getCastId(),
                  cast.getName(),
                  cast.getPhotoUrl(),
                  shift.getStartTime(),
                  shift.getEndTime());
            })
        .toList();
  }

  @Transactional
  public ShiftView create(Long storeId, CreateShift command, String actorEmail) {
    if (command.startTime().equals(command.endTime())) {
      throw new ServiceException("開始時刻と終了時刻が同一です");
    }
    requireActiveMembership(lockEnrollment(storeId, command.castId()));

    Shift shift =
        Shift.builder()
            .castId(command.castId())
            .workDate(command.workDate())
            .startTime(command.startTime())
            .endTime(command.endTime())
            .status(command.status() == null ? ShiftStatus.TENTATIVE : command.status())
            .published(command.published() == null || command.published())
            .createdBy(resolveActorId(actorEmail))
            .build();
    shift.setStoreId(storeId);
    shiftRepository.save(shift);
    shiftRepository.flush();
    audit.recordById(
        shift.getCreatedBy(),
        shift.getStoreId(),
        "SHIFT_CREATED",
        "SHIFT",
        shift.getId(),
        null,
        null,
        Map.of(),
        ShiftAuditSnapshot.of(shift).values());
    return toView(shift);
  }

  @Transactional
  public ShiftView update(Long storeId, String id, ShiftPatch patch, String actorEmail) {
    CastEnrollment target = patch.castId() == null ? null : lockEnrollment(storeId, patch.castId());

    // 実績の有無を読んで可否を決める以上、実績の記録・訂正と直列でなければ守衛は素通りされる。
    // ロックはこの取引でのシフトの最初の読み込みでなければならない（{@link ShiftRepository#findByIdAndStoreIdForUpdate}）。
    Shift shift = findShiftForUpdate(storeId, id);
    if (target != null && !target.getId().equals(shift.getCastId())) {
      requireActiveMembership(target);
    }

    // 部分更新のマージ結果（実効の開始・終了）で判定する。片方だけ来て既存値と一致する穴を塞ぐ。
    LocalTime effectiveStart = patch.startTime() != null ? patch.startTime() : shift.getStartTime();
    LocalTime effectiveEnd = patch.endTime() != null ? patch.endTime() : shift.getEndTime();
    if (effectiveStart != null && effectiveStart.equals(effectiveEnd)) {
      throw new ServiceException("開始時刻と終了時刻が同一です");
    }

    // 未取消の実績が付いたシフトは勤務日とキャストを変えられない。実績は記録時に営業日とキャストを物化して
    // おり、事後の付け替えはその事実と食い違う（ADR 0014）。逃げ道は実績の取消 → 変更 → 再記録。
    // 時刻・status は実績の有無と無関係に通す。
    if (movesAttribution(shift, patch) && attendanceRepository.hasActiveAttendance(storeId, id)) {
      throw new ServiceException("実績が記録されているシフトの勤務日とキャストは変更できません。実績を取り消してから変更してください");
    }

    var before = ShiftAuditSnapshot.of(shift);
    shift.apply(patch);
    shift.stampUpdatedBy(resolveActorId(actorEmail));

    boolean changed = !before.equals(ShiftAuditSnapshot.of(shift));
    shiftRepository.save(shift);
    shiftRepository.flush();
    if (changed)
      audit.recordById(
          shift.getUpdatedBy(),
          shift.getStoreId(),
          "SHIFT_UPDATED",
          "SHIFT",
          shift.getId(),
          null,
          null,
          before.values(),
          ShiftAuditSnapshot.of(shift).values());
    return toView(shift);
  }

  /** 店舗・在籍・シフトの順序を守り、作成と付け替えを退店から直列化する。 */
  private CastEnrollment lockEnrollment(Long storeId, String id) {
    storeRepository.lockAgainstDeletion(storeId);
    return enrollments
        .findByIdAndStoreIdForUpdate(id, storeId)
        .orElseThrow(() -> new NotFoundException("キャストが見つかりません: " + id));
  }

  private static void requireActiveMembership(CastEnrollment enrollment) {
    if (!enrollment.isMembershipActive()) {
      throw new ServiceException("退店済みの在籍にはシフトを作成・付け替えできません");
    }
  }

  /** 実績が物化した帰属（営業日・キャスト）を動かす更新か。現行と同値の再送は変更ではないので通す。 */
  private static boolean movesAttribution(Shift shift, ShiftPatch patch) {
    return (patch.workDate() != null && !patch.workDate().equals(shift.getWorkDate()))
        || (patch.castId() != null && !patch.castId().equals(shift.getCastId()));
  }

  /** 店外への露出可否は承認と独立した軸なので、状態や時間帯の更新とは別の口で受ける。 */
  @Transactional
  public ShiftView changePublication(
      Long storeId, String id, boolean published, String actorEmail) {
    Shift shift = findShift(storeId, id);
    var before = ShiftAuditSnapshot.of(shift);
    shift.changePublication(published);
    shift.stampUpdatedBy(resolveActorId(actorEmail));
    boolean changed = !before.equals(ShiftAuditSnapshot.of(shift));
    shiftRepository.save(shift);
    shiftRepository.flush();
    if (changed)
      audit.recordById(
          shift.getUpdatedBy(),
          shift.getStoreId(),
          "SHIFT_PUBLICATION_CHANGED",
          "SHIFT",
          shift.getId(),
          null,
          null,
          before.values(),
          ShiftAuditSnapshot.of(shift).values());
    return toView(shift);
  }

  @Transactional
  public void delete(Long storeId, String id) {
    Shift shift = findShiftForUpdate(storeId, id);
    var before = ShiftAuditSnapshot.of(shift);
    // 実績が参照する限り削除しない。取消済みも数えるのは、参照を外して消すと「予定通りの出勤」が
    // 「飛び込み」へ不可逆に化けるため（ADR 0014）。誤建の組は実績の取消と TENTATIVE 化で中性化する。
    if (attendanceRepository.existsByStoreIdAndShiftId(storeId, id)) {
      throw new ConflictException("実績が記録されているシフトは削除できません。実績を取り消したうえで下書きに戻してください");
    }
    recordUnlinkedRequests(storeId, id);
    shiftRepository.delete(shift);
    shiftRepository.flush();
    audit.recordCurrent(
        shift.getStoreId(),
        "SHIFT_DELETED",
        "SHIFT",
        id,
        before.values(),
        Map.of("exists", "false"));
  }

  private void recordUnlinkedRequests(Long storeId, String shiftId) {
    String afterId = "";
    while (true) {
      var page = requests.findLinkedForUpdate(storeId, shiftId, afterId, Limit.of(100));
      if (page.isEmpty()) return;
      for (var request : page) {
        var before = ShiftAuditSnapshot.of(request).values();
        var after = new HashMap<>(before);
        after.put("shift_id", "");
        // FKのSET NULLはversionを増やさない。削除が失敗すればこの監査も同じ取引で取り消される。
        audit.record(
            SecurityContextHolder.getContext().getAuthentication().getName(),
            request.getStoreId(),
            "SHIFT_REQUEST_UNLINKED",
            "SHIFT_REQUEST",
            request.getId(),
            "SHIFT",
            shiftId,
            before,
            after);
      }
      afterId = page.getLast().getId();
    }
  }

  /**
   * 予定を暦日付きの時刻ごと返す。境界は勤務日ごとに解決する — 日付変更時刻は夏時間の進みで飛ばされる帯に置くと その暦日だけ実際の境界が前へ送られる（{@link
   * BusinessDateService#effectiveDateChangeTimeOn}）。
   */
  private ShiftView toView(Shift shift) {
    return new ShiftView(shift, businessDateService.effectiveDateChangeTimeOn(shift.getWorkDate()));
  }

  private Shift findShift(Long storeId, String id) {
    return shiftRepository
        .findByIdAndStoreId(id, storeId)
        .orElseThrow(() -> new NotFoundException("シフトが見つかりません: " + id));
  }

  /** 予実の交差を触る操作のためにシフトを押さえて引く。契約は {@link ShiftRepository#findByIdAndStoreIdForUpdate} にある。 */
  private Shift findShiftForUpdate(Long storeId, String id) {
    return shiftRepository
        .findByIdAndStoreIdForUpdate(id, storeId)
        .orElseThrow(() -> new NotFoundException("シフトが見つかりません: " + id));
  }

  private Long resolveActorId(String actorEmail) {
    return userRepository
        .findByEmail(actorEmail)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
        .getId();
  }
}
