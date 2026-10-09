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
import com.kizuna.advertising.api.dto.AdvertisingResponses.MonthResponse;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingChange;
import com.kizuna.advertising.domain.AdvertisingCost;
import com.kizuna.advertising.domain.AdvertisingRequest;
import com.kizuna.advertising.infrastructure.AdvertisingRecords;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AdvertisingService {
  private final AdvertisingRecords records;
  private final StoreContext stores;
  private final ActorIdentityService actors;
  private final BusinessAudit audit;
  private final ObjectMapper json;
  private final Clock clock;
  private final AppProperties properties;

  @StoreScoped
  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.advertisingCost.readTimeoutSeconds}")
  public Page<CostSummary> list(String month, int page, int size) {
    return records
        .list(AdvertisingInput.month(month), AdvertisingInput.page(page, size))
        .map(CostSummary::of);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CostResponse get(String id) {
    return CostResponse.of(records.cost(id));
  }

  @StoreScoped
  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.advertisingCost.readTimeoutSeconds}")
  public MonthResponse month(String month) {
    return summary(AdvertisingInput.month(month));
  }

  private MonthResponse summary(String month) {
    long sales = 0, recruitment = 0, count = 0;
    for (var row : records.totals(month)) {
      long amount = (Long) row[1];
      if (row[0] == AdvertisingCategory.SALES) sales = amount;
      else recruitment = amount;
      count = Math.addExact(count, (Long) row[2]);
    }
    if (sales < 0 || recruitment < 0 || sales > 9_007_199_254_740_991L - recruitment)
      throw tooLarge();
    return new MonthResponse(
        stores.getStoreId(),
        month,
        revision(month),
        count,
        sales,
        recruitment,
        sales + recruitment,
        OffsetDateTime.now(clock));
  }

  private long revision(String month) {
    return records.month(month).map(m -> m.getRevision()).orElse(0L);
  }

  @StoreScoped
  @Transactional(timeoutString = "#{@appProperties.advertisingCost.readTimeoutSeconds}")
  public CostResponse create(CreateRequest request, String actor) {
    var month = AdvertisingInput.month(request.month());
    var values = request.values();
    return once(
        actor,
        request.requestId(),
        "CREATE",
        request,
        CostResponse.class,
        () -> {
          records.lockMonth(stores.getStoreId(), month);
          var cost = AdvertisingCost.builder().month(month).values(values).build();
          records.persist(cost);
          records.advance(month);
          records.flush();
          var after = CostResponse.of(cost);
          change(actor, month, "CREATED", null, after, null, null);
          return after;
        });
  }

  @StoreScoped
  @Transactional(timeoutString = "#{@appProperties.advertisingCost.readTimeoutSeconds}")
  public CostResponse replace(String id, ReplaceRequest request, String actor) {
    var values = request.values();
    return once(
        actor,
        request.requestId(),
        "REPLACE:" + id,
        request,
        CostResponse.class,
        () -> {
          var cost = locked(id);
          cost.requireVersion(request.version());
          var before = CostResponse.of(cost);
          cost.replace(values);
          records.advance(cost.getMonth());
          records.flush();
          var after = CostResponse.of(cost);
          change(actor, cost.getMonth(), "UPDATED", before, after, request.reason(), null);
          return after;
        });
  }

  @StoreScoped
  @Transactional(timeoutString = "#{@appProperties.advertisingCost.readTimeoutSeconds}")
  public void delete(String id, DeleteRequest request, String actor) {
    once(
        actor,
        request.requestId(),
        "DELETE:" + id,
        request,
        Boolean.class,
        () -> {
          var cost = locked(id);
          cost.requireVersion(request.version());
          var before = CostResponse.of(cost);
          cost.delete();
          records.advance(cost.getMonth());
          records.flush();
          change(actor, cost.getMonth(), "DELETED", before, null, request.reason(), null);
          return true;
        });
  }

  private AdvertisingCost locked(String id) {
    var cost = records.cost(id);
    records.lockMonth(stores.getStoreId(), cost.getMonth());
    return records.refresh(cost);
  }

  @StoreScoped
  @Transactional(timeoutString = "#{@appProperties.advertisingCost.readTimeoutSeconds}")
  public CopyResponse copy(String target, CopyRequest request, String actor) {
    AdvertisingInput.month(target);
    var parsed = YearMonth.parse(target);
    if (parsed.equals(YearMonth.of(1, 1))) throw new ServiceException("この月には前月がありません");
    String source = parsed.minusMonths(1).toString();
    return once(
        actor,
        request.requestId(),
        "COPY:" + target,
        request,
        CopyResponse.class,
        () -> {
          records.lockMonth(stores.getStoreId(), source);
          records.lockMonth(stores.getStoreId(), target);
          if (revision(source) != request.sourceVersion()
              || revision(target) != request.targetVersion())
            throw new ConflictException("コピー元または対象月が更新されています。再確認してください");
          if (records.count(target) > 0) throw new ConflictException("対象月に広告費があります。空の月にだけコピーできます");
          var originals = bounded(source);
          if (originals.isEmpty()) throw new ServiceException("前月にコピーできる広告費がありません");
          var targetMonth = records.advance(target);
          String copyId = UUID.randomUUID().toString();
          for (var original : originals) {
            var cost =
                AdvertisingCost.builder().month(target).values(original.values().copied()).build();
            records.persist(cost);
            records.flush();
            change(
                actor,
                target,
                "COPIED",
                null,
                CostResponse.of(cost),
                request.reason(),
                original.getId());
          }
          return new CopyResponse(
              copyId,
              stores.getStoreId(),
              source,
              target,
              originals.size(),
              request.sourceVersion(),
              targetMonth.getRevision(),
              OffsetDateTime.now(clock));
        });
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<ChangeSummary> changes(String month, String cursor, int requestedSize) {
    AdvertisingInput.month(month);
    int size = CursorPage.clampSize(requestedSize);
    return CursorPage.of(
            records.changes(month, cursor, size),
            size,
            c -> new PageCursor(c.getCreatedAt().toString(), c.getId()).encode())
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

  @StoreScoped
  @Transactional(readOnly = true)
  public ChangeResponse changeDetail(String month, String id) {
    var c = records.change(AdvertisingInput.month(month), id);
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

  public record ExportSnapshot(MonthResponse summary, List<CostResponse> costs) {}

  @StoreScoped
  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.advertisingCost.readTimeoutSeconds}")
  public ExportSnapshot snapshot(String month) {
    AdvertisingInput.month(month);
    var costs = bounded(month).stream().map(CostResponse::of).toList();
    return new ExportSnapshot(summary(month), costs);
  }

  private List<AdvertisingCost> bounded(String month) {
    int max = properties.getAdvertisingCost().getMaxOrders();
    if (max < 1 || max > 100_000) throw tooLarge();
    var rows = records.all(month, max);
    if (rows.size() > max) throw tooLarge();
    return rows;
  }

  private ServiceUnavailableException tooLarge() {
    return new ServiceUnavailableException("広告費が処理可能な上限を超えています。対象を確認してください");
  }

  private <T> T once(
      String actor, UUID key, String operation, Object request, Class<T> type, Supplier<T> work) {
    Long actorId = actors.requireUserId(actor);
    records.lock("request:" + stores.getStoreId() + ":" + actorId + ":" + key);
    String value = operation + "\n" + json.writeValueAsString(request);
    var receipt = records.receipt(actorId, key);
    if (receipt.isPresent()) {
      if (!receipt.get().getRequestValue().equals(value))
        throw new ConflictException("同じ要求識別子に別の内容は指定できません");
      return json.readValue(receipt.get().getResponseValue(), type);
    }
    T result = work.get();
    records.persist(
        AdvertisingRequest.builder()
            .actorId(actorId)
            .requestId(key)
            .requestValue(value)
            .responseValue(json.writeValueAsString(result))
            .build());
    records.flush();
    return json.readValue(json.writeValueAsString(result), type);
  }

  private void change(
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
    records.persist(c);
    records.flush();
    audit.record(
        actor,
        stores.getStoreId(),
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
