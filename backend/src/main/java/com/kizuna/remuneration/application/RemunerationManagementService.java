package com.kizuna.remuneration.application;

import com.kizuna.cast.remuneration.RemunerationPersonLookup;
import com.kizuna.receipt.once.Committed;
import com.kizuna.receipt.once.OperationReceipts;
import com.kizuna.receipt.once.Receipt;
import com.kizuna.receipt.once.ReceiptKey;
import com.kizuna.remuneration.application.RemunerationViews.BonusMutation;
import com.kizuna.remuneration.application.RemunerationViews.BonusView;
import com.kizuna.remuneration.application.RemunerationViews.ChangeView;
import com.kizuna.remuneration.application.RemunerationViews.GuaranteeList;
import com.kizuna.remuneration.application.RemunerationViews.GuaranteeMutation;
import com.kizuna.remuneration.application.RemunerationViews.GuaranteeView;
import com.kizuna.remuneration.domain.BonusAward;
import com.kizuna.remuneration.domain.GuaranteeTerm;
import com.kizuna.remuneration.domain.GuaranteeTimeline;
import com.kizuna.remuneration.domain.RemunerationChange;
import com.kizuna.remuneration.domain.RemunerationChangeRepository;
import com.kizuna.remuneration.domain.RemunerationCommand;
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
  public GuaranteeList guarantees(Long storeId, Long personId, Pageable pageable) {
    requirePerson(storeId, personId);
    return new GuaranteeList(
        records.timeline(storeId, personId).map(t -> t.getRevision()).orElse(0L),
        records.terms(storeId, personId, pageable).map(GuaranteeView::of));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<BonusView> bonuses(Long storeId, Long personId, String month, Pageable pageable) {
    requirePerson(storeId, personId);
    var target = RemunerationInput.month(month);
    return records
        .bonuses(storeId, personId, target.atDay(1), target.atEndOfMonth(), pageable)
        .map(BonusView::of);
  }

  @Transactional
  public GuaranteeMutation createGuarantee(
      Long storeId, RemunerationCommand.CreateGuarantee command, String actor) {
    return receipts.once(
        key(storeId, actor, command.requestId()),
        command.fingerprint(),
        () -> {
          requirePerson(storeId, command.personId());
          if (command.effectiveFrom().isBefore(dates.currentBusinessDate())) requireCorrection();
          var timeline = records.lockedTimeline(storeId, command.personId());
          timeline.advance(command.expectedVersion());
          var terms = records.activeTerms(storeId, command.personId());
          if (!terms.isEmpty()
              && !command.effectiveFrom().isAfter(terms.getLast().getEffectiveFrom()))
            throw new ConflictException("新しい保証条件は最後の適用開始日より後にしてください");
          var term =
              GuaranteeTerm.builder()
                  .personId(command.personId())
                  .date(command.effectiveFrom())
                  .state(command.state())
                  .amount(command.dailyAmount())
                  .reason(command.reason())
                  .build();
          term.setStoreId(storeId);
          records.persist(term);
          records.flush();
          var change =
              change(
                  storeId,
                  actor,
                  command.personId(),
                  "GUARANTEE",
                  term.getId(),
                  "CREATED",
                  command.reason(),
                  null,
                  GuaranteeView.of(term));
          return guaranteeCommitted("CREATED", term, timeline, change);
        },
        receipt -> guarantee(storeId, receipt));
  }

  @Transactional
  public GuaranteeMutation correctGuarantee(
      Long storeId, RemunerationCommand.CorrectGuarantee command, String actor) {
    var id = command.id();
    return receipts.once(
        key(storeId, actor, command.requestId()),
        command.fingerprint(),
        () -> {
          var term = records.term(storeId, id);
          requirePerson(storeId, term.getPersonId());
          var timeline = records.lockedTimeline(storeId, term.getPersonId());
          timeline.advance(command.expectedVersion());
          records.refreshForWrite(term);
          var before = GuaranteeView.of(term);
          for (var other : records.activeTerms(storeId, term.getPersonId())) {
            if (other.getId().equals(id)) continue;
            boolean previous = other.getEffectiveFrom().isBefore(term.getEffectiveFrom());
            if (previous && !command.effectiveFrom().isAfter(other.getEffectiveFrom())
                || !previous && !command.effectiveFrom().isBefore(other.getEffectiveFrom()))
              throw new ConflictException("訂正日が前後の保証条件と重なっています");
          }
          term.replace(
              command.effectiveFrom(), command.state(), command.dailyAmount(), command.reason());
          records.flush();
          var change =
              change(
                  storeId,
                  actor,
                  term.getPersonId(),
                  "GUARANTEE",
                  id,
                  "CORRECTED",
                  command.correctionReason(),
                  before,
                  GuaranteeView.of(term));
          return guaranteeCommitted("CORRECTED", term, timeline, change);
        },
        receipt -> guarantee(storeId, receipt));
  }

  @Transactional
  public GuaranteeMutation cancelGuarantee(
      Long storeId, RemunerationCommand.CancelGuarantee command, String actor) {
    var id = command.id();
    return receipts.once(
        key(storeId, actor, command.requestId()),
        command.fingerprint(),
        () -> {
          var term = records.term(storeId, id);
          requirePerson(storeId, term.getPersonId());
          var timeline = records.lockedTimeline(storeId, term.getPersonId());
          timeline.advance(command.expectedVersion());
          records.refreshForWrite(term);
          var before = GuaranteeView.of(term);
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
                  command.reason(),
                  before,
                  GuaranteeView.of(term));
          return guaranteeCommitted("CANCELLED", term, timeline, change);
        },
        receipt -> guarantee(storeId, receipt));
  }

  /** 保証条件の操作の要求キーの一意制約に敗れた要求を、成立済みの受領から解決する。 */
  @Transactional(readOnly = true)
  public GuaranteeMutation replayGuarantee(
      Long storeId, RemunerationCommand command, String actor) {
    return receipts.replayOnly(
        key(storeId, actor, command.requestId()),
        command.fingerprint(),
        receipt -> guarantee(storeId, receipt));
  }

  @Transactional
  public BonusMutation createBonus(
      Long storeId, RemunerationCommand.CreateBonus command, String actor) {
    return receipts.once(
        key(storeId, actor, command.requestId()),
        command.fingerprint(),
        () -> {
          requirePerson(storeId, command.personId());
          var bonus =
              BonusAward.builder()
                  .personId(command.personId())
                  .date(command.awardDate())
                  .amount(command.amount())
                  .reason(command.reason())
                  .build();
          bonus.setStoreId(storeId);
          records.persist(bonus);
          records.flush();
          var change =
              change(
                  storeId,
                  actor,
                  command.personId(),
                  "BONUS",
                  bonus.getId(),
                  "CREATED",
                  command.reason(),
                  null,
                  BonusView.of(bonus));
          return bonusCommitted("CREATED", bonus, change);
        },
        receipt -> bonus(storeId, receipt));
  }

  @Transactional
  public BonusMutation correctBonus(
      Long storeId, RemunerationCommand.CorrectBonus command, String actor) {
    var id = command.id();
    return receipts.once(
        key(storeId, actor, command.requestId()),
        command.fingerprint(),
        () -> {
          var bonus = records.bonus(storeId, id);
          requirePerson(storeId, bonus.getPersonId());
          records.refreshForWrite(bonus);
          bonus.requireVersion(command.expectedVersion());
          var before = BonusView.of(bonus);
          bonus.replace(command.awardDate(), command.amount(), command.reason());
          records.flush();
          var change =
              change(
                  storeId,
                  actor,
                  bonus.getPersonId(),
                  "BONUS",
                  id,
                  "CORRECTED",
                  command.correctionReason(),
                  before,
                  BonusView.of(bonus));
          return bonusCommitted("CORRECTED", bonus, change);
        },
        receipt -> bonus(storeId, receipt));
  }

  @Transactional
  public BonusMutation cancelBonus(
      Long storeId, RemunerationCommand.CancelBonus command, String actor) {
    var id = command.id();
    return receipts.once(
        key(storeId, actor, command.requestId()),
        command.fingerprint(),
        () -> {
          var bonus = records.bonus(storeId, id);
          requirePerson(storeId, bonus.getPersonId());
          records.refreshForWrite(bonus);
          bonus.requireVersion(command.expectedVersion());
          var before = BonusView.of(bonus);
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
                  command.reason(),
                  before,
                  BonusView.of(bonus));
          return bonusCommitted("CANCELLED", bonus, change);
        },
        receipt -> bonus(storeId, receipt));
  }

  /** ボーナスの操作の要求キーの一意制約に敗れた要求を、成立済みの受領から解決する。 */
  @Transactional(readOnly = true)
  public BonusMutation replayBonus(Long storeId, RemunerationCommand command, String actor) {
    return receipts.replayOnly(
        key(storeId, actor, command.requestId()),
        command.fingerprint(),
        receipt -> bonus(storeId, receipt));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<ChangeView> guaranteeChanges(Long storeId, String id, Pageable pageable) {
    requirePerson(storeId, records.term(storeId, id).getPersonId());
    return history(storeId, "GUARANTEE", id, pageable);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<ChangeView> bonusChanges(Long storeId, String id, Pageable pageable) {
    requirePerson(storeId, records.bonus(storeId, id).getPersonId());
    return history(storeId, "BONUS", id, pageable);
  }

  private Page<ChangeView> history(Long storeId, String type, String id, Pageable pageable) {
    return changes
        .findByStoreIdAndResourceTypeAndResourceId(storeId, type, id, pageable)
        .map(
            c ->
                new ChangeView(
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
  private GuaranteeMutation guarantee(Long storeId, Receipt receipt) {
    var term =
        records
            .findTerm(storeId, receipt.targetId())
            .orElseThrow(() -> new IllegalStateException("受領の対象の保証条件が見つかりません"));
    return new GuaranteeMutation(
        GuaranteeView.of(term),
        receipt.longFact("timeline_version"),
        receipt.textFact("change_id"));
  }

  private BonusMutation bonus(Long storeId, Receipt receipt) {
    var bonus =
        records
            .findBonus(storeId, receipt.targetId())
            .orElseThrow(() -> new IllegalStateException("受領の対象のボーナスが見つかりません"));
    return new BonusMutation(BonusView.of(bonus), receipt.textFact("change_id"));
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
