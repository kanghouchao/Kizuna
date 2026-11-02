package com.kizuna.remuneration.application;

import com.kizuna.cast.remuneration.RemunerationPersonLookup;
import com.kizuna.receipt.once.Committed;
import com.kizuna.receipt.once.OperationReceipts;
import com.kizuna.receipt.once.Receipt;
import com.kizuna.receipt.once.ReceiptKey;
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
import com.kizuna.remuneration.domain.GuaranteeTimeline;
import com.kizuna.remuneration.domain.RemunerationChange;
import com.kizuna.remuneration.domain.RemunerationChangeRepository;
import com.kizuna.remuneration.infrastructure.RemunerationRecords;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import java.util.Map;
import java.util.UUID;
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
  private final OperationReceipts receipts;
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
    return receipts.once(
        key(storeId, actor, request.requestId()),
        request.fingerprint(),
        () -> {
          requirePerson(storeId, request.personId());
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
                  GuaranteeResponse.of(term));
          return guaranteeCommitted("CREATED", term, timeline, change);
        },
        receipt -> guarantee(storeId, receipt));
  }

  @Transactional
  public GuaranteeMutationResponse correctGuarantee(
      Long storeId, String id, GuaranteeCorrectionRequest request, String actor) {
    return receipts.once(
        key(storeId, actor, request.requestId()),
        request.fingerprint(id),
        () -> {
          var term = records.term(storeId, id);
          requirePerson(storeId, term.getPersonId());
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
                  GuaranteeResponse.of(term));
          return guaranteeCommitted("CORRECTED", term, timeline, change);
        },
        receipt -> guarantee(storeId, receipt));
  }

  @Transactional
  public GuaranteeMutationResponse cancelGuarantee(
      Long storeId, String id, CancellationRequest request, String actor) {
    return receipts.once(
        key(storeId, actor, request.requestId()),
        request.guaranteeFingerprint(id),
        () -> {
          var term = records.term(storeId, id);
          requirePerson(storeId, term.getPersonId());
          var timeline = records.lockedTimeline(storeId, term.getPersonId());
          timeline.advance(request.expectedVersion());
          records.refreshForWrite(term);
          var before = GuaranteeResponse.of(term);
          term.cancel();
          records.flush();
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
                  GuaranteeResponse.of(term));
          return guaranteeCommitted("CANCELLED", term, timeline, change);
        },
        receipt -> guarantee(storeId, receipt));
  }

  /** 保証条件の操作の要求キーの一意制約に敗れた要求を、成立済みの受領から解決する。 */
  @Transactional(readOnly = true)
  public GuaranteeMutationResponse replayGuarantee(
      Long storeId, UUID requestId, String fingerprint, String actor) {
    return receipts.replayOnly(
        key(storeId, actor, requestId), fingerprint, receipt -> guarantee(storeId, receipt));
  }

  @Transactional
  public BonusMutationResponse createBonus(Long storeId, BonusCreateRequest request, String actor) {
    return receipts.once(
        key(storeId, actor, request.requestId()),
        request.fingerprint(),
        () -> {
          requirePerson(storeId, request.personId());
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
                  BonusResponse.of(bonus));
          return bonusCommitted("CREATED", bonus, change);
        },
        receipt -> bonus(storeId, receipt));
  }

  @Transactional
  public BonusMutationResponse correctBonus(
      Long storeId, String id, BonusCorrectionRequest request, String actor) {
    return receipts.once(
        key(storeId, actor, request.requestId()),
        request.fingerprint(id),
        () -> {
          var bonus = records.bonus(storeId, id);
          requirePerson(storeId, bonus.getPersonId());
          records.refreshForWrite(bonus);
          bonus.requireVersion(request.expectedVersion());
          var before = BonusResponse.of(bonus);
          bonus.replace(request.awardDate(), request.amount(), request.reason());
          records.flush();
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
                  BonusResponse.of(bonus));
          return bonusCommitted("CORRECTED", bonus, change);
        },
        receipt -> bonus(storeId, receipt));
  }

  @Transactional
  public BonusMutationResponse cancelBonus(
      Long storeId, String id, CancellationRequest request, String actor) {
    return receipts.once(
        key(storeId, actor, request.requestId()),
        request.bonusFingerprint(id),
        () -> {
          var bonus = records.bonus(storeId, id);
          requirePerson(storeId, bonus.getPersonId());
          records.refreshForWrite(bonus);
          bonus.requireVersion(request.expectedVersion());
          var before = BonusResponse.of(bonus);
          bonus.cancel();
          records.flush();
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
                  BonusResponse.of(bonus));
          return bonusCommitted("CANCELLED", bonus, change);
        },
        receipt -> bonus(storeId, receipt));
  }

  /** ボーナスの操作の要求キーの一意制約に敗れた要求を、成立済みの受領から解決する。 */
  @Transactional(readOnly = true)
  public BonusMutationResponse replayBonus(
      Long storeId, UUID requestId, String fingerprint, String actor) {
    return receipts.replayOnly(
        key(storeId, actor, requestId), fingerprint, receipt -> bonus(storeId, receipt));
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

  private ReceiptKey key(Long storeId, String actor, UUID requestId) {
    return new ReceiptKey(storeId, actors.requireUserId(actor), requestId.toString());
  }

  // 時間線の版と変更履歴の ID は成立時にしか分からないので受領の事実に持ち、条件の内容は現在の状態を読み直す。
  private static Committed guaranteeCommitted(
      String action, GuaranteeTerm term, GuaranteeTimeline timeline, RemunerationChange change) {
    return new Committed(
        "REMUNERATION_GUARANTEE_" + action,
        term.getId(),
        Map.of("timeline_version", timeline.getRevision(), "change_id", change.getId()));
  }

  private static Committed bonusCommitted(
      String action, BonusAward bonus, RemunerationChange change) {
    return new Committed(
        "REMUNERATION_BONUS_" + action, bonus.getId(), Map.of("change_id", change.getId()));
  }

  // 受領を持つ保証条件とボーナスは取消で終わるので、行が無いのは不変条件の破れであって業務拒否ではない。
  private GuaranteeMutationResponse guarantee(Long storeId, Receipt receipt) {
    var term =
        records
            .findTerm(storeId, receipt.targetId())
            .orElseThrow(() -> new IllegalStateException("受領の対象の保証条件が見つかりません"));
    return new GuaranteeMutationResponse(
        GuaranteeResponse.of(term),
        receipt.longFact("timeline_version"),
        receipt.textFact("change_id"));
  }

  private BonusMutationResponse bonus(Long storeId, Receipt receipt) {
    var bonus =
        records
            .findBonus(storeId, receipt.targetId())
            .orElseThrow(() -> new IllegalStateException("受領の対象のボーナスが見つかりません"));
    return new BonusMutationResponse(BonusResponse.of(bonus), receipt.textFact("change_id"));
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
