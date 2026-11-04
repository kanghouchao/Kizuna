package com.kizuna.point.application;

import com.kizuna.member.application.MemberLookupService;
import com.kizuna.point.domain.ExpiringCreditView;
import com.kizuna.point.domain.PointAllocation;
import com.kizuna.point.domain.PointAllocationRepository;
import com.kizuna.point.domain.PointConsumption;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.point.domain.PointLedger;
import com.kizuna.point.domain.PointLot;
import com.kizuna.shared.exception.ServiceException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NamedInterface("expiry")
public class PointExpiryLedger {
  private final PointEntryRepository entries;
  private final PointAllocationRepository allocations;
  private final MemberLookupService members;

  public record ExpiryEntry(Long id, Long memberId, int amount) {}

  public record ExpiringBalance(long memberId, long points) {}

  /**
   * {@code expiresOn} に期限を迎える未消費量を会員ごとに返す（期限前案内）。量は職員の会員画面と同じ期限別残高の定義で数え、
   * 台帳には書かない。ロックしないので、読んだ後の利用は数に反映されない。
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<ExpiringBalance> expiringOn(LocalDate expiresOn, LocalDate today, int maxLots) {
    if (maxLots < 1 || maxLots >= Integer.MAX_VALUE) {
      throw new ServiceException("期限前案内のロット上限が不正です");
    }
    var credits = entries.findUnconsumedCreditsExpiringOn(expiresOn, Limit.of(maxLots + 1));
    if (credits.size() > maxLots) {
      throw new ServiceException("期限前案内の対象が処理上限を超えています。実行設定を確認してください");
    }
    if (credits.isEmpty()) return List.of();
    Map<Long, Integer> consumed =
        allocations
            .findConsumedBySourceEntryIds(credits.stream().map(ExpiringCreditView::getId).toList())
            .stream()
            .collect(
                Collectors.toMap(
                    PointConsumption::getSourceEntryId, row -> Math.toIntExact(row.getConsumed())));
    var result = new ArrayList<ExpiringBalance>();
    credits.stream()
        .collect(
            Collectors.groupingBy(
                ExpiringCreditView::getMemberId, LinkedHashMap::new, Collectors.toList()))
        .forEach(
            (memberId, lots) ->
                new PointLedger(
                        lots.stream()
                            .map(
                                credit ->
                                    new PointLot(
                                        credit.getId(),
                                        credit.getAmount(),
                                        credit.getExpiresOn(),
                                        consumed.getOrDefault(credit.getId(), 0)))
                            .toList(),
                        today)
                    .balanceByExpiry().stream()
                        .filter(balance -> expiresOn.equals(balance.expiresOn()))
                        .forEach(
                            balance ->
                                result.add(new ExpiringBalance(memberId, balance.points()))));
    return List.copyOf(result);
  }

  /** 授権済み処理の取引へ参加し、監査に必要な識別子と記帳量だけを公開する。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<ExpiryEntry> materialize(LocalDate asOf, long executionId, int maxLots) {
    if (maxLots < 1 || maxLots >= Integer.MAX_VALUE) {
      throw new ServiceException("失効処理のロット上限が不正です");
    }
    var ids = entries.findExpiryCandidates(asOf, Limit.of(maxLots + 1));
    if (ids.size() > maxLots) {
      throw new ServiceException("失効対象が処理上限を超えています。実行設定を確認してください");
    }
    if (ids.isEmpty()) return List.of();

    // 会員行 → ロットの順で押さえる（退会・帰属の物化と同じ順序。逆だと失効仕訳の外部キー検査が会員行を待って死錠する）。
    // 消費・取消と同じロットを同じ順序でロックし、待機中に確定した引当も取得後に数える。
    entries.findMemberIdsOf(ids).stream().sorted().forEach(members::lockForBalanceConfirmation);
    var credits = entries.lockExpiryCandidates(ids);
    Map<Long, Integer> consumed =
        allocations.findConsumedBySourceEntryIds(ids).stream()
            .collect(
                Collectors.toMap(
                    PointConsumption::getSourceEntryId, row -> Math.toIntExact(row.getConsumed())));
    var byMember =
        credits.stream()
            .collect(
                Collectors.groupingBy(
                    PointEntry::getMemberId, LinkedHashMap::new, Collectors.toList()));
    var result = new ArrayList<ExpiryEntry>();
    for (var member : byMember.entrySet()) {
      var lots =
          member.getValue().stream()
              .map(
                  credit ->
                      new PointLot(
                          credit.getId(),
                          credit.getAmount(),
                          credit.getExpiresOn(),
                          consumed.getOrDefault(credit.getId(), 0)))
              .toList();
      var plan = new PointLedger(lots, asOf).planExpiry();
      if (plan.isEmpty()) continue;
      int points = Math.toIntExact(plan.stream().mapToLong(p -> p.amount()).sum());
      List<PointAllocation> allocationRows =
          plan.stream().map(p -> PointAllocation.of(p.sourceEntryId(), p.amount())).toList();
      var entry =
          entries.saveAndFlush(
              PointEntry.expire(
                  member.getKey(),
                  points,
                  allocationRows,
                  "expiry:" + executionId + ":" + member.getKey()));
      result.add(new ExpiryEntry(entry.getId(), member.getKey(), entry.getAmount()));
    }
    return List.copyOf(result);
  }
}
