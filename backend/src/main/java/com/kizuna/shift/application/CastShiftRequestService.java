package com.kizuna.shift.application;

import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreScopeExempt;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.shift.api.dto.CastShiftRequestResponse;
import com.kizuna.shift.api.dto.ShiftChangeRequestCreateRequest;
import com.kizuna.shift.api.dto.ShiftRequestCreateRequest;
import com.kizuna.shift.api.dto.ShiftRequestMapper;
import com.kizuna.shift.api.dto.ShiftRequestResponse;
import com.kizuna.shift.domain.CastShiftRequestView;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftRequestRepository;
import com.kizuna.shift.domain.ShiftRequestType;
import com.kizuna.shift.domain.ShiftStatus;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本人（キャスト）の出勤希望ユースケース（新規希望・変更申請の提出、履歴）。
 *
 * <p>提出は StoreContext が確立していない {@code /platform/me} 配下のため、対象店舗への所属は「当該店舗に本人の cast
 * 行が存在すること」で判定し、{@link ShiftRequest} の store_id を明示設定する（{@code @StoreScoped} を経由しないため
 * StoreScopeStampListener の自動採番に頼れない）。
 */
@Service
@RequiredArgsConstructor
public class CastShiftRequestService {

  private final PlatformUserRepository platformUserRepository;
  private final CastRepository castRepository;
  private final ShiftRequestRepository shiftRequestRepository;
  private final ShiftRepository shiftRepository;
  private final ShiftRequestMapper shiftRequestMapper;
  private final AppProperties appProperties;

  @StoreScopeExempt(reason = "所属判定は「指定店舗に本人の cast 行が存在すること」で行い、store_id はその判定に通った店舗を明示設定する")
  @Transactional
  public ShiftRequestResponse submit(String email, ShiftRequestCreateRequest request) {
    validateRequestedSlot(request.getWorkDate(), request.getStartTime(), request.getEndTime());

    Long userId = resolveUserId(email);
    // 同一店舗に本人の档案が複数並存し得るため、最古の档案 id を決定的に選ぶ（リポジトリが古い順で返す）。
    String castId =
        castRepository.findIdsByPlatformUserIdAndStoreId(userId, request.getStoreId()).stream()
            .findFirst()
            .orElseThrow(() -> new ServiceException("指定店舗に所属していません"));

    ShiftRequest entity =
        ShiftRequest.builder()
            .castId(castId)
            .workDate(request.getWorkDate())
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .note(request.getNote())
            .build();
    entity.setStoreId(request.getStoreId());
    return shiftRequestMapper.toResponse(shiftRequestRepository.save(entity));
  }

  /**
   * 確定シフトへの変更申請を提出する。対象シフトは本人の cast 行に属し（cast_id 自限がそのまま店舗自限として機能する）、かつ CONFIRMED
   * であることを要求する。店舗はシフトから導出し、シフト自体はこの時点では変更しない（店舗の承認で更新される）。
   */
  @StoreScopeExempt(reason = "対象シフトを本人の cast_id 集合に属するものだけへ絞ることが境界で、store_id はそのシフトから導出する")
  @Transactional
  public ShiftRequestResponse submitChange(String email, ShiftChangeRequestCreateRequest request) {
    validateRequestedSlot(request.getWorkDate(), request.getStartTime(), request.getEndTime());

    Long userId = resolveUserId(email);
    List<String> castIds = castRepository.findIdsByPlatformUserId(userId);
    Shift shift =
        shiftRepository
            .findById(request.getShiftId())
            .filter(s -> castIds.contains(s.getCastId()))
            .orElseThrow(() -> new NotFoundException("対象のシフトが見つかりません: " + request.getShiftId()));
    if (shift.getStatus() != ShiftStatus.CONFIRMED) {
      throw new ServiceException("確定済みのシフトのみ変更申請できます");
    }

    ShiftRequest entity =
        ShiftRequest.builder()
            .castId(shift.getCastId())
            .type(ShiftRequestType.CHANGE)
            .shiftId(shift.getId())
            // 申請時点の対象シフトの時間帯を控える。承認時にここから変わっていれば適用を拒む（陳腐化した申請で店舗編集を上書きしない）。
            .originalWorkDate(shift.getWorkDate())
            .originalStartTime(shift.getStartTime())
            .originalEndTime(shift.getEndTime())
            .workDate(request.getWorkDate())
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .note(request.getNote())
            .build();
    entity.setStoreId(shift.getStoreId());
    return shiftRequestMapper.toResponse(shiftRequestRepository.save(entity));
  }

  /**
   * 本人の出勤希望履歴。続きはカーソルで辿る。
   *
   * <p>履歴は提出のたびに増え続けるので、上限の無い一覧では返さない。
   *
   * @param cursor 続きの位置。null なら先頭から
   * @param requestedSize 1 回に返す件数の希望値（上限に丸められる）
   */
  @StoreScopeExempt(reason = "本人の cast_id 集合の一致を問い合わせ自体に載せることが唯一の境界（cast 自限が店舗自限としても働く）")
  @Transactional(readOnly = true)
  public CursorPage<CastShiftRequestResponse> history(
      String email, String cursor, int requestedSize) {
    Long userId = resolveUserId(email);
    List<String> castIds = castRepository.findIdsByPlatformUserId(userId);
    if (castIds.isEmpty()) {
      return new CursorPage<>(List.of(), null);
    }
    int size = CursorPage.clampSize(requestedSize);
    // 続きの有無は上限より 1 件多く取って判る。総件数の問い合わせを毎回撒かずに済む。
    Limit limit = Limit.of(size + 1);
    List<CastShiftRequestView> fetched =
        cursor == null
            ? shiftRequestRepository.findHistoryByCastIds(castIds, limit)
            : fetchHistoryAfter(castIds, PageCursor.decode(cursor), limit);
    return CursorPage.of(fetched, size, CastShiftRequestService::cursorOf)
        .map(shiftRequestMapper::toHistoryResponse);
  }

  private List<CastShiftRequestView> fetchHistoryAfter(
      List<String> castIds, PageCursor cursor, Limit limit) {
    return shiftRequestRepository.findHistoryByCastIdsAfter(
        castIds, cursor.timestampKey(), cursor.id(), limit);
  }

  /** 続きの位置は一覧の並び（提出時刻 + id）と同じ組で作る。組が並びとずれると、続きが手前へ戻るか行を飛ばす。 */
  private static String cursorOf(CastShiftRequestView view) {
    return new PageCursor(view.getCreatedAt().toString(), view.getId()).encode();
  }

  /** 希望する勤務枠の共通検証（新規希望・変更申請共通）: 開始≠終了、勤務日は本日以降。 */
  private void validateRequestedSlot(LocalDate workDate, LocalTime startTime, LocalTime endTime) {
    if (startTime.equals(endTime)) {
      throw new ServiceException("開始時刻と終了時刻が同一です");
    }
    LocalDate today = LocalDate.now(ZoneId.of(appProperties.getTimezone()));
    if (workDate.isBefore(today)) {
      throw new ServiceException("勤務日は本日以降を指定してください");
    }
  }

  private Long resolveUserId(String email) {
    return platformUserRepository
        .findByEmail(email)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
        .getId();
  }
}
