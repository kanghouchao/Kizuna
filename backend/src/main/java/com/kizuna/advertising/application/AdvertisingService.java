package com.kizuna.advertising.application;

import com.kizuna.advertising.api.dto.AdvertisingRequests.CopyRequest;
import com.kizuna.advertising.api.dto.AdvertisingRequests.CreateRequest;
import com.kizuna.advertising.api.dto.AdvertisingRequests.DeleteRequest;
import com.kizuna.advertising.api.dto.AdvertisingRequests.ReplaceRequest;
import com.kizuna.advertising.api.dto.AdvertisingResponses.ChangeResponse;
import com.kizuna.advertising.api.dto.AdvertisingResponses.ChangeSummary;
import com.kizuna.advertising.api.dto.AdvertisingResponses.CopyResponse;
import com.kizuna.advertising.api.dto.AdvertisingResponses.CostResponse;
import com.kizuna.advertising.api.dto.AdvertisingResponses.CostSummary;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingChange;
import com.kizuna.advertising.domain.AdvertisingChangeRepository;
import com.kizuna.advertising.domain.AdvertisingCost;
import com.kizuna.advertising.domain.AdvertisingMedium;
import com.kizuna.advertising.infrastructure.AdvertisingRecords;
import com.kizuna.medium.lookup.MediumLookup;
import com.kizuna.medium.lookup.MediumReference;
import com.kizuna.receipt.once.Committed;
import com.kizuna.receipt.once.OperationReceipts;
import com.kizuna.receipt.once.Receipt;
import com.kizuna.receipt.once.ReceiptKey;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AdvertisingService {
  private final AdvertisingRecords records;
  private final MediumLookup mediumLookup;
  private final AdvertisingChangeRepository changes;
  private final OperationReceipts receipts;
  private final ActorIdentityService actors;
  private final BusinessAudit audit;
  private final ObjectMapper json;
  private final Clock clock;
  private final AppProperties properties;

  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.advertisingCost.readTimeoutSeconds}")
  public Page<CostSummary> list(Long storeId, String month, Pageable pageable) {
    var page = records.list(storeId, AdvertisingInput.month(month), pageable);
    var names = media(storeId, page.getContent());
    return page.map(c -> CostSummary.of(c, MediumLookup.referenced(names, c.getMediumId()).name()));
  }

  @Transactional(readOnly = true)
  public CostResponse get(Long storeId, String id) {
    return response(storeId, records.cost(storeId, id));
  }

  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.advertisingCost.readTimeoutSeconds}")
  public MonthTotals month(Long storeId, String month) {
    return summary(storeId, AdvertisingInput.month(month));
  }

  private MonthTotals summary(Long storeId, String month) {
    long sales = 0, recruitment = 0, count = 0;
    for (var row : records.totals(storeId, month)) {
      long amount = (Long) row[1];
      if (row[0] == AdvertisingCategory.SALES) sales = amount;
      else recruitment = amount;
      count = Math.addExact(count, (Long) row[2]);
    }
    if (sales < 0 || recruitment < 0 || sales > 9_007_199_254_740_991L - recruitment)
      throw tooLarge();
    return new MonthTotals(
        storeId,
        month,
        revision(storeId, month),
        count,
        sales,
        recruitment,
        sales + recruitment,
        OffsetDateTime.now(clock));
  }

  private long revision(Long storeId, String month) {
    return records.month(storeId, month).map(m -> m.getRevision()).orElse(0L);
  }

  @Transactional(timeoutString = "#{@appProperties.advertisingCost.readTimeoutSeconds}")
  public CostResponse create(Long storeId, CreateRequest request, String actor) {
    var month = AdvertisingInput.month(request.month());
    var values = request.values();
    return receipts.once(
        key(storeId, actor, request.requestId()),
        request.fingerprint(),
        () -> {
          var selected = mediumLookup.findAll(storeId, Set.of(values.mediumId()));
          MediumLookup.selectable(selected.get(values.mediumId()), null);
          records.lockMonth(storeId, month);
          var cost = AdvertisingCost.builder().month(month).values(values).build();
          cost.setStoreId(storeId);
          records.persist(cost);
          records.advance(storeId, month);
          MediumLookup.flush(records::flush, DbConstraint.FK_T_ADVERTISING_COSTS_MEDIUM);
          var after =
              CostResponse.of(cost, MediumLookup.referenced(selected, cost.getMediumId()).name());
          change(storeId, actor, month, "CREATED", null, after, null, null);
          return committed("CREATED", cost);
        },
        receipt -> recorded(storeId, receipt));
  }

  @Transactional(timeoutString = "#{@appProperties.advertisingCost.readTimeoutSeconds}")
  public CostResponse replace(Long storeId, String id, ReplaceRequest request, String actor) {
    var values = request.values();
    return receipts.once(
        key(storeId, actor, request.requestId()),
        request.fingerprint(id),
        () -> {
          var cost = locked(storeId, id);
          cost.requireVersion(request.version());
          var current = cost.getMediumId();
          var selected =
              mediumLookup.findAll(
                  storeId, Stream.of(current, values.mediumId()).collect(Collectors.toSet()));
          MediumLookup.selectable(selected.get(values.mediumId()), current);
          var before = CostResponse.of(cost, MediumLookup.referenced(selected, current).name());
          cost.replace(values);
          records.advance(storeId, cost.getMonth());
          MediumLookup.flush(records::flush, DbConstraint.FK_T_ADVERTISING_COSTS_MEDIUM);
          var after =
              CostResponse.of(cost, MediumLookup.referenced(selected, cost.getMediumId()).name());
          change(storeId, actor, cost.getMonth(), "UPDATED", before, after, request.reason(), null);
          return committed("UPDATED", cost);
        },
        receipt -> recorded(storeId, receipt));
  }

  /** 作成と置換の要求キーの一意制約に敗れた要求を、成立済みの受領から解決する。 */
  @Transactional(readOnly = true)
  public CostResponse replayCost(Long storeId, UUID requestId, String fingerprint, String actor) {
    return receipts.replayOnly(
        key(storeId, actor, requestId), fingerprint, receipt -> recorded(storeId, receipt));
  }

  @Transactional(timeoutString = "#{@appProperties.advertisingCost.readTimeoutSeconds}")
  public void delete(Long storeId, String id, DeleteRequest request, String actor) {
    receipts.once(
        key(storeId, actor, request.requestId()),
        request.fingerprint(id),
        () -> {
          var cost = locked(storeId, id);
          cost.requireVersion(request.version());
          var before = response(storeId, cost);
          cost.delete();
          records.advance(storeId, cost.getMonth());
          records.flush();
          change(storeId, actor, cost.getMonth(), "DELETED", before, null, request.reason(), null);
          return committed("DELETED", cost);
        },
        receipt -> null);
  }

  @Transactional(readOnly = true)
  public void replayDelete(Long storeId, String id, DeleteRequest request, String actor) {
    receipts.replayOnly(
        key(storeId, actor, request.requestId()), request.fingerprint(id), receipt -> null);
  }

  private AdvertisingCost locked(Long storeId, String id) {
    var cost = records.cost(storeId, id);
    records.lockMonth(storeId, cost.getMonth());
    return records.refresh(cost);
  }

  @Transactional(timeoutString = "#{@appProperties.advertisingCost.readTimeoutSeconds}")
  public CopyResponse copy(Long storeId, String target, CopyRequest request, String actor) {
    String source = source(target);
    return receipts.once(
        key(storeId, actor, request.requestId()),
        request.fingerprint(target),
        () -> {
          records.lockMonth(storeId, source);
          records.lockMonth(storeId, target);
          if (revision(storeId, source) != request.sourceVersion()
              || revision(storeId, target) != request.targetVersion())
            throw new ConflictException("コピー元または対象月が更新されています。再確認してください");
          if (records.count(storeId, target) > 0)
            throw new ConflictException("対象月に広告費があります。空の月にだけコピーできます");
          var originals = bounded(storeId, source);
          if (originals.isEmpty()) throw new ServiceException("前月にコピーできる広告費がありません");
          var names = media(storeId, originals);
          var targetMonth = records.advance(storeId, target);
          for (var original : originals) {
            var cost =
                AdvertisingCost.builder().month(target).values(original.values().copied()).build();
            cost.setStoreId(storeId);
            records.persist(cost);
            records.flush();
            change(
                storeId,
                actor,
                target,
                "COPIED",
                null,
                CostResponse.of(cost, MediumLookup.referenced(names, cost.getMediumId()).name()),
                request.reason(),
                original.getId());
          }
          return new Committed(
              "ADVERTISING_COST_COPIED",
              target,
              Map.of(
                  "copy_id",
                  UUID.randomUUID().toString(),
                  "copied_count",
                  originals.size(),
                  "source_version",
                  request.sourceVersion(),
                  "target_version",
                  targetMonth.getRevision(),
                  "created_at",
                  OffsetDateTime.now(clock).toString()));
        },
        receipt -> copied(storeId, receipt));
  }

  @Transactional(readOnly = true)
  public CopyResponse replayCopy(Long storeId, String target, CopyRequest request, String actor) {
    source(target);
    return receipts.replayOnly(
        key(storeId, actor, request.requestId()),
        request.fingerprint(target),
        receipt -> copied(storeId, receipt));
  }

  private static String source(String target) {
    AdvertisingInput.month(target);
    var parsed = YearMonth.parse(target);
    if (parsed.equals(YearMonth.of(1, 1))) throw new ServiceException("この月には前月がありません");
    return parsed.minusMonths(1).toString();
  }

  // コピーの ID・件数・成立時の各月の版は行や履歴に残らないので、受領の事実だけから組む。
  private static CopyResponse copied(Long storeId, Receipt receipt) {
    return new CopyResponse(
        receipt.textFact("copy_id"),
        storeId,
        source(receipt.targetId()),
        receipt.targetId(),
        Math.toIntExact(receipt.longFact("copied_count")),
        receipt.longFact("source_version"),
        receipt.longFact("target_version"),
        OffsetDateTime.parse(receipt.textFact("created_at")));
  }

  @Transactional(readOnly = true)
  public Page<ChangeSummary> changes(Long storeId, String month, Pageable pageable) {
    return changes
        .findByStoreIdAndMonth(storeId, AdvertisingInput.month(month), pageable)
        .map(
            c ->
                new ChangeSummary(
                    c.getId(),
                    c.getCostId(),
                    c.getAction(),
                    c.getActorId(),
                    c.getCreatedAt(),
                    c.getVersionBefore(),
                    c.getVersionAfter()));
  }

  @Transactional(readOnly = true)
  public ChangeResponse changeDetail(Long storeId, String month, String id) {
    var c = records.change(storeId, AdvertisingInput.month(month), id);
    return new ChangeResponse(
        c.getId(),
        c.getCostId(),
        c.getAction(),
        c.getActorId(),
        c.getCreatedAt(),
        c.getVersionBefore(),
        c.getVersionAfter(),
        c.getReason(),
        c.getSourceCostId(),
        json.readValue(c.getBeforeValue(), CostResponse.class),
        json.readValue(c.getAfterValue(), CostResponse.class));
  }

  /** 出力時点の対象月の総計と、有効な広告費の全件。媒体名は現在名。 */
  public record ExportSnapshot(MonthTotals summary, List<ExportedCost> costs) {}

  public record MonthTotals(
      Long storeId,
      String month,
      long version,
      long entryCount,
      long salesAmount,
      long recruitmentAmount,
      long recordedTotalAmount,
      OffsetDateTime generatedAt) {}

  public record ExportedCost(
      String id,
      long version,
      AdvertisingCategory category,
      AdvertisingMedium medium,
      String agencyName,
      String planName,
      Integer inquiryCount,
      int amount) {
    static ExportedCost of(AdvertisingCost c, String mediumName) {
      return new ExportedCost(
          c.getId(),
          c.getVersion(),
          c.getCategory(),
          new AdvertisingMedium(c.getMediumId(), mediumName),
          c.getAgencyName(),
          c.getPlanName(),
          c.getInquiryCount(),
          c.getAmount());
    }
  }

  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.advertisingCost.readTimeoutSeconds}")
  public ExportSnapshot snapshot(Long storeId, String month) {
    AdvertisingInput.month(month);
    var rows = bounded(storeId, month);
    var names = media(storeId, rows);
    var costs =
        rows.stream()
            .map(c -> ExportedCost.of(c, MediumLookup.referenced(names, c.getMediumId()).name()))
            .toList();
    return new ExportSnapshot(summary(storeId, month), costs);
  }

  private CostResponse response(Long storeId, AdvertisingCost cost) {
    var names = media(storeId, List.of(cost));
    return CostResponse.of(cost, MediumLookup.referenced(names, cost.getMediumId()).name());
  }

  private Map<String, MediumReference> media(Long storeId, Collection<AdvertisingCost> costs) {
    return mediumLookup.findAll(
        storeId, costs.stream().map(AdvertisingCost::getMediumId).collect(Collectors.toSet()));
  }

  private List<AdvertisingCost> bounded(Long storeId, String month) {
    int max = properties.getAdvertisingCost().getMaxOrders();
    if (max < 1 || max > 100_000) throw tooLarge();
    var rows = records.all(storeId, month, max);
    if (rows.size() > max) throw tooLarge();
    return rows;
  }

  private ServiceUnavailableException tooLarge() {
    return new ServiceUnavailableException("広告費が処理可能な上限を超えています。対象を確認してください");
  }

  private ReceiptKey key(Long storeId, String actor, UUID requestId) {
    return new ReceiptKey(storeId, actors.requireUserId(actor), requestId.toString());
  }

  private static Committed committed(String action, AdvertisingCost cost) {
    return new Committed(
        "ADVERTISING_COST_" + action, cost.getId(), Map.of("committed_version", cost.getVersion()));
  }

  // 受領を持つ広告費は論理削除で終わるので、行が無いのは不変条件の破れであって業務拒否ではない。
  private CostResponse recorded(Long storeId, Receipt receipt) {
    var cost =
        records
            .recorded(storeId, receipt.targetId())
            .orElseThrow(() -> new IllegalStateException("受領の対象の広告費が見つかりません"));
    return response(storeId, cost);
  }

  private void change(
      Long storeId,
      String actor,
      String month,
      String action,
      CostResponse before,
      CostResponse after,
      String reason,
      String source) {
    String id = after == null ? before.id() : after.id();
    var c =
        AdvertisingChange.builder()
            .month(month)
            .costId(id)
            .actorId(actors.requireUserId(actor))
            .action(action)
            .reason(reason)
            .sourceCostId(source)
            .versionBefore(before == null ? null : before.version())
            .versionAfter(after == null ? null : after.version())
            .beforeValue(json.writeValueAsString(before))
            .afterValue(json.writeValueAsString(after))
            .build();
    c.setStoreId(storeId);
    records.persist(c);
    records.flush();
    audit.record(
        actor,
        storeId,
        "ADVERTISING_COST_" + action,
        "ADVERTISING_COST",
        id,
        "ADVERTISING_CHANGE",
        c.getId(),
        auditValues(before),
        auditValues(after));
  }

  private Map<String, String> auditValues(CostResponse c) {
    return c == null
        ? Map.of()
        : Map.of(
            "month",
            c.month(),
            "version",
            Long.toString(c.version()),
            "category",
            c.category().name(),
            "amount",
            Integer.toString(c.amount()));
  }
}
