package com.kizuna.shift.application;

import com.kizuna.cast.application.CastService;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shift.api.dto.PublicShiftResponse;
import com.kizuna.shift.api.dto.ShiftCreateRequest;
import com.kizuna.shift.api.dto.ShiftMapper;
import com.kizuna.shift.api.dto.ShiftResponse;
import com.kizuna.shift.api.dto.ShiftUpdateRequest;
import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftStatus;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShiftService {

  private final ShiftRepository shiftRepository;
  private final AttendanceRepository attendanceRepository;
  private final ShiftMapper shiftMapper;
  private final CastService castService;
  private final CastRepository castRepository;
  private final PlatformUserRepository platformUserRepository;
  private final BusinessDateService businessDateService;

  @StoreScoped
  @Transactional(readOnly = true)
  public List<ShiftResponse> list(LocalDate from, LocalDate to) {
    return shiftRepository.findByWorkDateBetween(from, to).stream()
        .map(shiftMapper::toResponse)
        .toList();
  }

  /**
   * 公開出勤表用に「現在の営業日」の露出可能（CONFIRMED ∧ 公開可）なシフトを start_time 昇順で返す。 ACTIVE でないキャストのシフトは公開一覧 ({@code
   * /store/casts/public}) に整合させて除外する。cast 表示情報は公開されている cast.domain（{@link Cast}）を
   * 直接参照して結合する（cast.api.dto は公開面ではないため）。storeFilter は {@code @StoreScoped} によりセッション全体で有効なので t_casts
   * 参照も現店舗に絞られる。
   */
  @StoreScoped
  @Transactional(readOnly = true)
  public List<PublicShiftResponse> listPublicToday() {
    List<Shift> shifts =
        shiftRepository.findByWorkDateAndStatusAndPublishedTrueOrderByStartTimeAsc(
            businessDateService.currentBusinessDate(), ShiftStatus.CONFIRMED);
    if (shifts.isEmpty()) {
      return List.of();
    }
    Map<String, Cast> activeCasts =
        castRepository.findByStatusOrderByDisplayOrderAsc("ACTIVE").stream()
            .collect(Collectors.toMap(Cast::getId, Function.identity()));
    return shifts.stream()
        .filter(shift -> activeCasts.containsKey(shift.getCastId()))
        .map(
            shift -> {
              Cast cast = activeCasts.get(shift.getCastId());
              return PublicShiftResponse.builder()
                  .castId(shift.getCastId())
                  .castName(cast.getName())
                  .castPhotoUrl(cast.getPhotoUrl())
                  .startTime(shift.getStartTime())
                  .endTime(shift.getEndTime())
                  .build();
            })
        .toList();
  }

  @StoreScoped
  @Transactional
  public ShiftResponse create(ShiftCreateRequest request, String actorEmail) {
    if (request.getStartTime().equals(request.getEndTime())) {
      throw new ServiceException("開始時刻と終了時刻が同一です");
    }
    if (!castService.existsForCurrentStore(request.getCastId())) {
      throw new NotFoundException("キャストが見つかりません: " + request.getCastId());
    }

    // store_id は StoreScopeStampListener が @PrePersist で採番する
    Shift shift = shiftMapper.toEntity(request, resolveActorId(actorEmail));
    return shiftMapper.toResponse(shiftRepository.save(shift));
  }

  @StoreScoped
  @Transactional
  public ShiftResponse update(String id, ShiftUpdateRequest request, String actorEmail) {
    // 付け替え先のキャストはシフトより先に押さえる。この更新は cast_id を書くので、書き込みが行き先の
    // キャスト行に key share を要求する — シフトを先に押さえると、キャスト → シフト の順で進む記録と
    // 環になる（契約は CastRepository#findScopedByIdForUpdate）。
    if (request.getCastId() != null
        && !castService.existsForCurrentStoreForUpdate(request.getCastId())) {
      throw new NotFoundException("キャストが見つかりません: " + request.getCastId());
    }

    // 実績の有無を読んで可否を決める以上、実績の記録・訂正と直列でなければ守衛は素通りされる。
    // ロックはこの取引でのシフトの最初の読み込みでなければならない（{@link ShiftRepository#findScopedByIdForUpdate}）。
    Shift shift = findShiftForUpdate(id);

    // 部分更新のマージ結果（実効の開始・終了）で判定する。片方だけ来て既存値と一致する穴を塞ぐ。
    LocalTime effectiveStart =
        request.getStartTime() != null ? request.getStartTime() : shift.getStartTime();
    LocalTime effectiveEnd =
        request.getEndTime() != null ? request.getEndTime() : shift.getEndTime();
    if (effectiveStart != null && effectiveStart.equals(effectiveEnd)) {
      throw new ServiceException("開始時刻と終了時刻が同一です");
    }

    // 未取消の実績が付いたシフトは勤務日とキャストを変えられない。実績は記録時に営業日とキャストを物化して
    // おり、事後の付け替えはその事実と食い違う（ADR 0014）。逃げ道は実績の取消 → 変更 → 再記録。
    // 時刻・status は実績の有無と無関係に通す。
    if (movesAttribution(shift, request) && attendanceRepository.hasActiveAttendance(id)) {
      throw new ServiceException("実績が記録されているシフトの勤務日とキャストは変更できません。実績を取り消してから変更してください");
    }

    shift.apply(shiftMapper.toPatch(request));
    shift.stampUpdatedBy(resolveActorId(actorEmail));

    return shiftMapper.toResponse(shiftRepository.save(shift));
  }

  /** 実績が物化した帰属（営業日・キャスト）を動かす更新か。現行と同値の再送は変更ではないので通す。 */
  private static boolean movesAttribution(Shift shift, ShiftUpdateRequest request) {
    return (request.getWorkDate() != null && !request.getWorkDate().equals(shift.getWorkDate()))
        || (request.getCastId() != null && !request.getCastId().equals(shift.getCastId()));
  }

  /**
   * 店外への露出可否を切り替える。承認とは独立の軸なので、状態や時間帯の更新とは別の口で受ける（ADR 0015）。
   *
   * <p>専用の留痕は持たないが、行を書いた操作の実行者を印字する規則には従う。
   */
  @StoreScoped
  @Transactional
  public ShiftResponse changePublication(String id, boolean published, String actorEmail) {
    Shift shift = findShift(id);
    shift.changePublication(published);
    shift.stampUpdatedBy(resolveActorId(actorEmail));
    return shiftMapper.toResponse(shiftRepository.save(shift));
  }

  @StoreScoped
  @Transactional
  public void delete(String id) {
    findShiftForUpdate(id);
    // 実績が参照する限り削除しない。取消済みも数えるのは、参照を外して消すと「予定通りの出勤」が
    // 「飛び込み」へ不可逆に化けるため（ADR 0014）。誤建の組は実績の取消と TENTATIVE 化で中性化する。
    if (attendanceRepository.existsByShiftId(id)) {
      throw new ConflictException("実績が記録されているシフトは削除できません。実績を取り消したうえで下書きに戻してください");
    }
    shiftRepository.deleteById(id);
  }

  private Shift findShift(String id) {
    return shiftRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("シフトが見つかりません: " + id));
  }

  /** 予実の交差を触る操作のためにシフトを押さえて引く。契約は {@link ShiftRepository#findScopedByIdForUpdate} にある。 */
  private Shift findShiftForUpdate(String id) {
    return shiftRepository
        .findScopedByIdForUpdate(id)
        .orElseThrow(() -> new NotFoundException("シフトが見つかりません: " + id));
  }

  private Long resolveActorId(String actorEmail) {
    return platformUserRepository
        .findByEmail(actorEmail)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
        .getId();
  }
}
