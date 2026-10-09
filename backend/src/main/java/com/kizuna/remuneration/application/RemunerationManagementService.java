package com.kizuna.remuneration.application;

import com.kizuna.cast.remuneration.RemunerationPersonLookup;
import com.kizuna.remuneration.api.dto.BonusMutationResponse;
import com.kizuna.remuneration.api.dto.BonusResponse;
import com.kizuna.remuneration.api.dto.GuaranteeListResponse;
import com.kizuna.remuneration.api.dto.GuaranteeMutationResponse;
import com.kizuna.remuneration.api.dto.GuaranteeResponse;
import com.kizuna.remuneration.api.dto.RemunerationChangeResponse;
import com.kizuna.remuneration.api.dto.RemunerationRequests.BonusCorrectionRequest;
import com.kizuna.remuneration.api.dto.RemunerationRequests.BonusCreateRequest;
import com.kizuna.remuneration.api.dto.RemunerationRequests.CancellationRequest;
import com.kizuna.remuneration.api.dto.RemunerationRequests.GuaranteeCorrectionRequest;
import com.kizuna.remuneration.api.dto.RemunerationRequests.GuaranteeCreateRequest;
import com.kizuna.remuneration.domain.BonusAward;
import com.kizuna.remuneration.domain.GuaranteeTerm;
import com.kizuna.remuneration.domain.RemunerationChange;
import com.kizuna.remuneration.domain.RemunerationRequest;
import com.kizuna.remuneration.infrastructure.RemunerationRecords;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class RemunerationManagementService {
  private final RemunerationRecords records;
  private final RemunerationPersonLookup people;
  private final StoreContext stores;
  private final ActorIdentityService actors;
  private final BusinessDateService dates;
  private final BusinessAudit audit;
  private final ObjectMapper json;

  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public GuaranteeListResponse guarantees(Long personId, int page, int size) {
    requirePerson(personId);
    return new GuaranteeListResponse(
        records.timeline(store(), personId).map(t -> t.getRevision()).orElse(0L),
        records
            .terms(store(), personId, RemunerationInput.page(page, size))
            .map(GuaranteeResponse::of));
  }

  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<BonusResponse> bonuses(Long personId, String month, int page, int size) {
    requirePerson(personId);
    var target = RemunerationInput.month(month);
    return records
        .bonuses(
            store(),
            personId,
            target.atDay(1),
            target.atEndOfMonth(),
            RemunerationInput.page(page, size))
        .map(BonusResponse::of);
  }

  @StoreScoped
  @Transactional
  public GuaranteeMutationResponse createGuarantee(GuaranteeCreateRequest request, String actor) {
    requirePerson(request.personId());
    return once(
        actor,
        request.requestId(),
        "GUARANTEE_CREATE",
        request,
        GuaranteeMutationResponse.class,
        () -> {
          if (request.effectiveFrom().isBefore(dates.currentBusinessDate())) requireCorrection();
          var timeline = records.lockedTimeline(store(), request.personId());
          timeline.advance(request.expectedVersion());
          var terms = records.activeTerms(store(), request.personId());
          if (!terms.isEmpty()
              && !request.effectiveFrom().isAfter(terms.getLast().getEffectiveFrom()))
            throw new ConflictException("新しい保証条件は最後の適用開始日より後にしてください");
          var term =
              GuaranteeTerm.builder()
                  .personId(request.personId())
                  .date(request.effectiveFrom())
                  .state(request.state())
                  .amount(request.dailyAmount())
                  .reason(request.reason())
                  .build();
          records.persist(term);
          records.flush();
          var result = GuaranteeResponse.of(term);
          var change =
              change(
                  actor,
                  request.personId(),
                  "GUARANTEE",
                  term.getId(),
                  "CREATED",
                  request.reason(),
                  null,
                  result);
          return new GuaranteeMutationResponse(result, timeline.getRevision(), change.getId());
        });
  }

  @StoreScoped
  @Transactional
  public GuaranteeMutationResponse correctGuarantee(
      String id, GuaranteeCorrectionRequest request, String actor) {
    var term = records.term(store(), id);
    requirePerson(term.getPersonId());
    return once(
        actor,
        request.requestId(),
        "GUARANTEE_CORRECT:" + id,
        request,
        GuaranteeMutationResponse.class,
        () -> {
          var timeline = records.lockedTimeline(store(), term.getPersonId());
          timeline.advance(request.expectedVersion());
          records.refreshForWrite(term);
          var before = GuaranteeResponse.of(term);
          for (var other : records.activeTerms(store(), term.getPersonId())) {
            if (other.getId().equals(id)) continue;
            boolean previous = other.getEffectiveFrom().isBefore(term.getEffectiveFrom());
            if (previous && !request.effectiveFrom().isAfter(other.getEffectiveFrom())
                || !previous && !request.effectiveFrom().isBefore(other.getEffectiveFrom()))
              throw new ConflictException("訂正日が前後の保証条件と重なっています");
          }
          term.replace(
              request.effectiveFrom(), request.state(), request.dailyAmount(), request.reason());
          records.flush();
          var result = GuaranteeResponse.of(term);
          var change =
              change(
                  actor,
                  term.getPersonId(),
                  "GUARANTEE",
                  id,
                  "CORRECTED",
                  request.correctionReason(),
                  before,
                  result);
          return new GuaranteeMutationResponse(result, timeline.getRevision(), change.getId());
        });
  }

  @StoreScoped
  @Transactional
  public GuaranteeMutationResponse cancelGuarantee(
      String id, CancellationRequest request, String actor) {
    var term = records.term(store(), id);
    requirePerson(term.getPersonId());
    return once(
        actor,
        request.requestId(),
        "GUARANTEE_CANCEL:" + id,
        request,
        GuaranteeMutationResponse.class,
        () -> {
          var timeline = records.lockedTimeline(store(), term.getPersonId());
          timeline.advance(request.expectedVersion());
          records.refreshForWrite(term);
          var before = GuaranteeResponse.of(term);
          term.cancel();
          records.flush();
          var result = GuaranteeResponse.of(term);
          var change =
              change(
                  actor,
                  term.getPersonId(),
                  "GUARANTEE",
                  id,
                  "CANCELLED",
                  request.reason(),
                  before,
                  result);
          return new GuaranteeMutationResponse(result, timeline.getRevision(), change.getId());
        });
  }

  @StoreScoped
  @Transactional
  public BonusMutationResponse createBonus(BonusCreateRequest request, String actor) {
    requirePerson(request.personId());
    return once(
        actor,
        request.requestId(),
        "BONUS_CREATE",
        request,
        BonusMutationResponse.class,
        () -> {
          var bonus =
              BonusAward.builder()
                  .personId(request.personId())
                  .date(request.awardDate())
                  .amount(request.amount())
                  .reason(request.reason())
                  .build();
          records.persist(bonus);
          records.flush();
          var result = BonusResponse.of(bonus);
          var change =
              change(
                  actor,
                  request.personId(),
                  "BONUS",
                  bonus.getId(),
                  "CREATED",
                  request.reason(),
                  null,
                  result);
          return new BonusMutationResponse(result, change.getId());
        });
  }

  @StoreScoped
  @Transactional
  public BonusMutationResponse correctBonus(
      String id, BonusCorrectionRequest request, String actor) {
    var bonus = records.bonus(store(), id);
    requirePerson(bonus.getPersonId());
    return once(
        actor,
        request.requestId(),
        "BONUS_CORRECT:" + id,
        request,
        BonusMutationResponse.class,
        () -> {
          records.refreshForWrite(bonus);
          bonus.requireVersion(request.expectedVersion());
          var before = BonusResponse.of(bonus);
          bonus.replace(request.awardDate(), request.amount(), request.reason());
          records.flush();
          var result = BonusResponse.of(bonus);
          var change =
              change(
                  actor,
                  bonus.getPersonId(),
                  "BONUS",
                  id,
                  "CORRECTED",
                  request.correctionReason(),
                  before,
                  result);
          return new BonusMutationResponse(result, change.getId());
        });
  }

  @StoreScoped
  @Transactional
  public BonusMutationResponse cancelBonus(String id, CancellationRequest request, String actor) {
    var bonus = records.bonus(store(), id);
    requirePerson(bonus.getPersonId());
    return once(
        actor,
        request.requestId(),
        "BONUS_CANCEL:" + id,
        request,
        BonusMutationResponse.class,
        () -> {
          records.refreshForWrite(bonus);
          bonus.requireVersion(request.expectedVersion());
          var before = BonusResponse.of(bonus);
          bonus.cancel();
          records.flush();
          var result = BonusResponse.of(bonus);
          var change =
              change(
                  actor,
                  bonus.getPersonId(),
                  "BONUS",
                  id,
                  "CANCELLED",
                  request.reason(),
                  before,
                  result);
          return new BonusMutationResponse(result, change.getId());
        });
  }

  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CursorPage<RemunerationChangeResponse> guaranteeChanges(
      String id, String cursor, int size) {
    requirePerson(records.term(store(), id).getPersonId());
    return history("GUARANTEE", id, cursor, size);
  }

  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CursorPage<RemunerationChangeResponse> bonusChanges(String id, String cursor, int size) {
    requirePerson(records.bonus(store(), id).getPersonId());
    return history("BONUS", id, cursor, size);
  }

  private CursorPage<RemunerationChangeResponse> history(
      String type, String id, String cursor, int size) {
    RemunerationInput.page(0, size);
    return CursorPage.of(
            records.changes(store(), type, id, cursor, size),
            size,
            c -> c.getCreatedAt() + "|" + c.getId())
        .map(
            c ->
                new RemunerationChangeResponse(
                    c.getId(),
                    c.getActorId(),
                    c.getAction(),
                    c.getReason(),
                    json.readTree(c.getBeforeValue()),
                    json.readTree(c.getAfterValue()),
                    c.getCreatedAt()));
  }

  private <T> T once(
      String actor,
      UUID requestId,
      String operation,
      Object request,
      Class<T> type,
      Supplier<T> work) {
    Long actorId = actors.requireUserId(actor);
    records.lock("request:" + store() + ":" + actorId + ":" + requestId);
    String value = operation + "\n" + json.writeValueAsString(request);
    var prior = records.receipt(store(), actorId, requestId);
    if (prior.isPresent()) {
      if (!prior.get().getRequestValue().equals(value))
        throw new ConflictException("同じリクエスト識別子に別の内容は指定できません");
      return json.readValue(prior.get().getResponseValue(), type);
    }
    T result = work.get();
    records.persist(
        RemunerationRequest.builder()
            .actorId(actorId)
            .requestId(requestId)
            .requestValue(value)
            .responseValue(json.writeValueAsString(result))
            .build());
    records.flush();
    return result;
  }

  private RemunerationChange change(
      String actor,
      Long person,
      String type,
      String id,
      String action,
      String reason,
      Object before,
      Object after) {
    var change =
        RemunerationChange.builder()
            .personId(person)
            .actorId(actors.requireUserId(actor))
            .type(type)
            .id(id)
            .action(action)
            .reason(reason)
            .before(json.writeValueAsString(before))
            .after(json.writeValueAsString(after))
            .build();
    records.persist(change);
    records.flush();
    audit.record(
        actor,
        store(),
        "REMUNERATION_" + type + "_" + action,
        type,
        id,
        "REMUNERATION_CHANGE",
        change.getId(),
        Map.of("value", json.writeValueAsString(before)),
        Map.of("value", json.writeValueAsString(after), "reason", reason.strip()));
    return change;
  }

  private Long store() {
    return stores.getStoreId();
  }

  private void requirePerson(Long person) {
    RemunerationInput.person(person);
    people.require(store(), person);
  }

  private void requireCorrection() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null
        || auth.getAuthorities().stream()
            .noneMatch(a -> a.getAuthority().equals("PERM_REMUNERATION_CORRECT")))
      throw new AccessDeniedException("過去日の保証条件には訂正権限が必要です");
  }
}
