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
import com.kizuna.remuneration.domain.RemunerationChangeRepository;
import com.kizuna.remuneration.domain.RemunerationRequest;
import com.kizuna.remuneration.infrastructure.RemunerationRecords;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
  private final RemunerationChangeRepository changes;
  private final RemunerationPersonLookup people;
  private final ActorIdentityService actors;
  private final BusinessDateService dates;
  private final BusinessAudit audit;
  private final ObjectMapper json;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public GuaranteeListResponse guarantees(Long storeId, Long personId, Pageable pageable) {
    requirePerson(storeId, personId);
    return new GuaranteeListResponse(
        records.timeline(storeId, personId).map(t -> t.getRevision()).orElse(0L),
        records.terms(storeId, personId, pageable).map(GuaranteeResponse::of));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<BonusResponse> bonuses(Long storeId, Long personId, String month, Pageable pageable) {
    requirePerson(storeId, personId);
    var target = RemunerationInput.month(month);
    return records
        .bonuses(storeId, personId, target.atDay(1), target.atEndOfMonth(), pageable)
        .map(BonusResponse::of);
  }

  @Transactional
  public GuaranteeMutationResponse createGuarantee(
      Long storeId, GuaranteeCreateRequest request, String actor) {
    requirePerson(storeId, request.personId());
    return once(
        storeId,
        actor,
        request.requestId(),
        "GUARANTEE_CREATE",
        request,
        GuaranteeMutationResponse.class,
        () -> {
          if (request.effectiveFrom().isBefore(dates.currentBusinessDate())) requireCorrection();
          var timeline = records.lockedTimeline(storeId, request.personId());
          timeline.advance(request.expectedVersion());
          var terms = records.activeTerms(storeId, request.personId());
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
          term.setStoreId(storeId);
          records.persist(term);
          records.flush();
          var result = GuaranteeResponse.of(term);
          var change =
              change(
                  storeId,
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

  @Transactional
  public GuaranteeMutationResponse correctGuarantee(
      Long storeId, String id, GuaranteeCorrectionRequest request, String actor) {
    var term = records.term(storeId, id);
    requirePerson(storeId, term.getPersonId());
    return once(
        storeId,
        actor,
        request.requestId(),
        "GUARANTEE_CORRECT:" + id,
        request,
        GuaranteeMutationResponse.class,
        () -> {
          var timeline = records.lockedTimeline(storeId, term.getPersonId());
          timeline.advance(request.expectedVersion());
          records.refreshForWrite(term);
          var before = GuaranteeResponse.of(term);
          for (var other : records.activeTerms(storeId, term.getPersonId())) {
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
                  storeId,
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

  @Transactional
  public GuaranteeMutationResponse cancelGuarantee(
      Long storeId, String id, CancellationRequest request, String actor) {
    var term = records.term(storeId, id);
    requirePerson(storeId, term.getPersonId());
    return once(
        storeId,
        actor,
        request.requestId(),
        "GUARANTEE_CANCEL:" + id,
        request,
        GuaranteeMutationResponse.class,
        () -> {
          var timeline = records.lockedTimeline(storeId, term.getPersonId());
          timeline.advance(request.expectedVersion());
          records.refreshForWrite(term);
          var before = GuaranteeResponse.of(term);
          term.cancel();
          records.flush();
          var result = GuaranteeResponse.of(term);
          var change =
              change(
                  storeId,
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

  @Transactional
  public BonusMutationResponse createBonus(Long storeId, BonusCreateRequest request, String actor) {
    requirePerson(storeId, request.personId());
    return once(
        storeId,
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
          bonus.setStoreId(storeId);
          records.persist(bonus);
          records.flush();
          var result = BonusResponse.of(bonus);
          var change =
              change(
                  storeId,
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

  @Transactional
  public BonusMutationResponse correctBonus(
      Long storeId, String id, BonusCorrectionRequest request, String actor) {
    var bonus = records.bonus(storeId, id);
    requirePerson(storeId, bonus.getPersonId());
    return once(
        storeId,
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
                  storeId,
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

  @Transactional
  public BonusMutationResponse cancelBonus(
      Long storeId, String id, CancellationRequest request, String actor) {
    var bonus = records.bonus(storeId, id);
    requirePerson(storeId, bonus.getPersonId());
    return once(
        storeId,
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
                  storeId,
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

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<RemunerationChangeResponse> guaranteeChanges(
      Long storeId, String id, Pageable pageable) {
    requirePerson(storeId, records.term(storeId, id).getPersonId());
    return history(storeId, "GUARANTEE", id, pageable);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<RemunerationChangeResponse> bonusChanges(Long storeId, String id, Pageable pageable) {
    requirePerson(storeId, records.bonus(storeId, id).getPersonId());
    return history(storeId, "BONUS", id, pageable);
  }

  private Page<RemunerationChangeResponse> history(
      Long storeId, String type, String id, Pageable pageable) {
    return changes
        .findByStoreIdAndResourceTypeAndResourceId(storeId, type, id, pageable)
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
      Long storeId,
      String actor,
      UUID requestId,
      String operation,
      Object request,
      Class<T> type,
      Supplier<T> work) {
    Long actorId = actors.requireUserId(actor);
    records.lock("request:" + storeId + ":" + actorId + ":" + requestId);
    String value = operation + "\n" + json.writeValueAsString(request);
    var prior = records.receipt(storeId, actorId, requestId);
    if (prior.isPresent()) {
      if (!prior.get().getRequestValue().equals(value))
        throw new ConflictException("同じリクエスト識別子に別の内容は指定できません");
      return json.readValue(prior.get().getResponseValue(), type);
    }
    T result = work.get();
    var receipt =
        RemunerationRequest.builder()
            .actorId(actorId)
            .requestId(requestId)
            .requestValue(value)
            .responseValue(json.writeValueAsString(result))
            .build();
    receipt.setStoreId(storeId);
    records.persist(receipt);
    records.flush();
    return result;
  }

  private RemunerationChange change(
      Long storeId,
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
    change.setStoreId(storeId);
    records.persist(change);
    records.flush();
    audit.record(
        actor,
        storeId,
        "REMUNERATION_" + type + "_" + action,
        type,
        id,
        "REMUNERATION_CHANGE",
        change.getId(),
        Map.of("value", json.writeValueAsString(before)),
        Map.of("value", json.writeValueAsString(after), "reason", reason.strip()));
    return change;
  }

  private void requirePerson(Long storeId, Long person) {
    RemunerationInput.person(person);
    people.require(storeId, person);
  }

  private void requireCorrection() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null
        || auth.getAuthorities().stream()
            .noneMatch(a -> a.getAuthority().equals("PERM_REMUNERATION_CORRECT")))
      throw new AccessDeniedException("過去日の保証条件には訂正権限が必要です");
  }
}
