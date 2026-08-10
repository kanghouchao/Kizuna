package com.kizuna.point.application;

import com.kizuna.point.domain.PlannedAllocation;
import com.kizuna.point.domain.PointAllocation;
import com.kizuna.point.domain.PointAllocationRepository;
import com.kizuna.point.domain.PointConsumption;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.point.domain.PointLedger;
import com.kizuna.point.domain.PointLot;
import com.kizuna.settings.application.PointSettings;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ポイント台帳への書き込みと残高照会の受け口。
 *
 * <p>伝播は既定の REQUIRED。受注完了のように「オーダーの確定とポイントの付与が同時に成立するか、どちらも成立しないか」を
 * 求める呼出が呼び元のトランザクションへ合流できるようにするため、 イベントによる非同期化はしていない。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PointLedgerService {

  private final PointEntryRepository pointEntryRepository;
  private final PointAllocationRepository pointAllocationRepository;
  private final SystemConfigService systemConfigService;

  /**
   * 会計金額に対して付与されるポイント数。台帳へは書き込まない。
   *
   * <p>付与基準は入力された会計金額そのもので、ポイント利用による控除は差し引かない。
   *
   * <p>設定が未投入（単位金額または単位あたり付与が 0 以下）なら 0 を返す — 設定の不備で受注完了そのものが失敗しないようにする。
   */
  @Transactional(readOnly = true)
  public int previewGrant(int totalFee) {
    PointSettings settings = systemConfigService.pointSettings();
    if (settings.grantUnitAmount() <= 0 || settings.grantPointsPerUnit() <= 0) {
      return 0;
    }
    return Math.floorDiv(totalFee, settings.grantUnitAmount()) * settings.grantPointsPerUnit();
  }

  /** ポイント利用の単位。設定が 0 以下なら 1（＝単位の制約なし）とみなす。 */
  @Transactional(readOnly = true)
  public int usageUnit() {
    int unit = systemConfigService.pointSettings().usageUnit();
    return unit <= 0 ? 1 : unit;
  }

  /** 会員の現在残高。 */
  @Transactional(readOnly = true)
  public int balance(long memberId) {
    return ledgerOf(pointEntryRepository.findCredits(memberId)).balance();
  }

  /** 受注完了に伴う付与。付与が 0 なら台帳へ何も書かない。戻り値は実際に付与したポイント数。 */
  public int grantForOrder(
      long memberId, String orderId, Long storeId, int totalFee, Long actorUserId) {
    int granted = previewGrant(totalFee);
    if (granted == 0) {
      return 0;
    }
    pointEntryRepository.save(
        PointEntry.grantForOrder(memberId, orderId, storeId, granted, actorUserId));
    return granted;
  }

  /** 受注会計でのポイント利用。期限の早いロットから引き当てる。 */
  public void useForOrder(
      long memberId, String orderId, Long storeId, int points, Long actorUserId) {
    int unit = usageUnit();
    if (points <= 0 || points % unit != 0) {
      throw new ServiceException("利用ポイントは " + unit + " ポイント単位で指定してください");
    }
    List<PlannedAllocation> plan = lockedLedgerOf(memberId).planConsumption(points);
    pointEntryRepository.save(
        PointEntry.useForOrder(
            memberId, orderId, storeId, points, allocationsOf(plan), actorUserId));
  }

  /** 運用者による手動調整。加算は新しいロットになり、減算は既存ロットを期限の早い順に引き当てる。 */
  public void adjust(
      long memberId,
      Long storeId,
      int delta,
      String reason,
      LocalDate expiresOn,
      Long actorUserId) {
    if (delta == 0) {
      throw new ServiceException("増減は 0 以外で指定してください");
    }
    if (delta > 0) {
      pointEntryRepository.save(
          PointEntry.manualAdjust(
              memberId, storeId, delta, reason, expiresOn, List.of(), actorUserId));
      return;
    }
    if (expiresOn != null) {
      throw new ServiceException("減算の調整に有効期限は指定できません");
    }
    List<PlannedAllocation> plan = lockedLedgerOf(memberId).planConsumption(-delta);
    pointEntryRepository.save(
        PointEntry.manualAdjust(
            memberId, storeId, delta, reason, null, allocationsOf(plan), actorUserId));
  }

  /**
   * 加算仕訳の取消。未消費分だけを打ち消す仕訳を積み、元の行は書き換えない。
   *
   * <p>取消も引き当てを書く消費なので、未消費分を数える前に消費経路と同じ行ロックを取る。取らないと、並行する利用が
   * 同じ残りを引き当てた後に取消が古い未消費分を打ち消し、ロットの引き当て合計が加算量を超える。
   */
  public void cancel(long entryId, Long actorUserId) {
    PointEntry original =
        pointEntryRepository
            .findById(entryId)
            .orElseThrow(() -> new NotFoundException("ポイント仕訳が見つかりません"));
    pointEntryRepository.findCreditsForUpdate(original.getMemberId());
    int available = original.getAmount() - consumedBy(List.of(entryId)).getOrDefault(entryId, 0);
    pointEntryRepository.save(PointEntry.cancel(original, available, actorUserId));
  }

  /**
   * 受注を根拠とするすべての付与（別経路の付与が order_id を持つ場合を含む）を取消仕訳で無効化する。利用（USE）の再付与は 完了後訂正の意味論が定まってから —
   * 取消できるのは残余のある付与のみ。
   *
   * <p>消費し切った付与と取消済みの付与は残余が 0 なので黙って飛ばす。付与の無い受注も同じく何もしない。
   */
  public void cancelForOrder(String orderId, Long actorUserId) {
    List<PointEntry> credits = pointEntryRepository.findCreditsByOrderId(orderId);
    if (credits.isEmpty()) {
      return;
    }
    // 単発の取消と同じく、未消費分を数える前に消費経路と同じ行ロックを取る。
    credits.stream()
        .map(PointEntry::getMemberId)
        .distinct()
        .forEach(pointEntryRepository::findCreditsForUpdate);
    Map<Long, Integer> consumed = consumedBy(credits.stream().map(PointEntry::getId).toList());
    for (PointEntry credit : credits) {
      int available = credit.getAmount() - consumed.getOrDefault(credit.getId(), 0);
      if (available <= 0) {
        continue;
      }
      pointEntryRepository.save(PointEntry.cancel(credit, available, actorUserId));
    }
  }

  /** 消費のために加算ロットを行ロック付きで読み直した台帳。 */
  private PointLedger lockedLedgerOf(long memberId) {
    return ledgerOf(pointEntryRepository.findCreditsForUpdate(memberId));
  }

  private PointLedger ledgerOf(List<PointEntry> credits) {
    return new PointLedger(lotsOf(credits), LocalDate.now());
  }

  private List<PointLot> lotsOf(List<PointEntry> credits) {
    if (credits.isEmpty()) {
      return List.of();
    }
    Map<Long, Integer> consumed = consumedBy(credits.stream().map(PointEntry::getId).toList());
    return credits.stream()
        .map(
            credit ->
                new PointLot(
                    credit.getId(),
                    credit.getAmount(),
                    credit.getExpiresOn(),
                    consumed.getOrDefault(credit.getId(), 0)))
        .toList();
  }

  private Map<Long, Integer> consumedBy(List<Long> creditIds) {
    return pointAllocationRepository.findConsumedBySourceEntryIds(creditIds).stream()
        .collect(
            Collectors.toMap(
                PointConsumption::getSourceEntryId, row -> row.getConsumed().intValue()));
  }

  private static List<PointAllocation> allocationsOf(List<PlannedAllocation> plan) {
    return plan.stream()
        .map(planned -> PointAllocation.of(planned.sourceEntryId(), planned.amount()))
        .toList();
  }
}
