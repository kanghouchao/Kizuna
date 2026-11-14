package com.kizuna.survey.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.receipt.once.Committed;
import com.kizuna.receipt.once.Fingerprint;
import com.kizuna.receipt.once.OperationReceipts;
import com.kizuna.receipt.once.Receipt;
import com.kizuna.receipt.once.ReceiptKey;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import com.kizuna.survey.application.SurveyViews.AnswerWritten;
import com.kizuna.survey.application.SurveyViews.RevisionWritten;
import com.kizuna.survey.application.SurveyViews.Written;
import com.kizuna.survey.domain.SurveyAnswer;
import com.kizuna.survey.domain.SurveyAnswerDetailView;
import com.kizuna.survey.domain.SurveyAnswerRepository;
import com.kizuna.survey.domain.SurveyAnswers;
import com.kizuna.survey.domain.SurveyCommand;
import com.kizuna.survey.domain.SurveyCountsView;
import com.kizuna.survey.domain.SurveyDefinition;
import com.kizuna.survey.domain.SurveyHistory;
import com.kizuna.survey.domain.SurveyHistoryRepository;
import com.kizuna.survey.domain.SurveyInput;
import com.kizuna.survey.domain.SurveyRevision;
import com.kizuna.survey.domain.SurveyRevisionDetailView;
import com.kizuna.survey.domain.SurveyRevisionRepository;
import com.kizuna.survey.domain.SurveySeries;
import com.kizuna.survey.domain.SurveySeriesRepository;
import com.kizuna.survey.domain.SurveyValues.Operation;
import com.kizuna.survey.domain.SurveyValues.RevisionStatus;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SurveyService {
  private final SurveySeriesRepository surveys;
  private final SurveyRevisionRepository revisions;
  private final SurveyAnswerRepository answers;
  private final OperationReceipts receipts;
  private final SurveyHistoryRepository history;
  private final ActorIdentityService identities;
  private final BusinessAudit audit;
  private final Clock clock;

  @Transactional
  public Written write(Long storeId, String email, SurveyCommand command) {
    var actor = identities.requireActor(email);
    return receipts.once(
        new ReceiptKey(storeId, actor.id(), command.key()),
        fingerprint(command),
        () -> {
          lock(storeId, command);
          var result =
              switch (normalize(storeId, command)) {
                case SurveyCommand.Create c -> create(storeId, email, actor, c);
                case SurveyCommand.Revise c -> revise(storeId, email, actor, c);
                case SurveyCommand.Replace c -> replace(storeId, email, actor, c);
                case SurveyCommand.RevisionAction c -> transition(storeId, email, actor, c);
                case SurveyCommand.Receive c -> receive(storeId, email, actor, c);
                case SurveyCommand.Withdraw c -> withdraw(storeId, email, actor, c);
                case SurveyCommand.Correct c -> correct(storeId, email, actor, c);
              };
          StoreScopedEntity row = result.revision() == null ? result.answer() : result.revision();
          return new Committed(
              result.type().receiptName(),
              row.getId(),
              Map.of("committed_version", row.getVersion()));
        },
        receipt -> respond(storeId, receipt));
  }

  @Transactional(readOnly = true)
  public Written replayOnly(Long storeId, String email, SurveyCommand command) {
    var actor = identities.requireActor(email);
    return receipts.replayOnly(
        new ReceiptKey(storeId, actor.id(), command.key()),
        fingerprint(command),
        receipt -> respond(storeId, receipt));
  }

  @Transactional(readOnly = true)
  public SurveyRevisionDetailView revision(Long storeId, String surveyId, String id) {
    return revisions
        .findProjectedByIdAndStoreIdAndSurveyId(
            SurveyInput.id(id), storeId, SurveyInput.id(surveyId))
        .orElseThrow(SurveyService::missing);
  }

  @Transactional(readOnly = true)
  public SurveyAnswerDetailView answer(Long storeId, String id) {
    return answers
        .findProjectedByIdAndStoreId(SurveyInput.id(id), storeId)
        .orElseThrow(SurveyService::missing);
  }

  @Transactional(readOnly = true)
  public SurveyCountsView counts(Long storeId, String surveyId, String id) {
    if (!revisions.existsByIdAndStoreIdAndSurveyId(
        SurveyInput.id(id), storeId, SurveyInput.id(surveyId))) throw missing();
    return answers.counts(storeId, id);
  }

  private record Result(SurveyRevision revision, SurveyAnswer answer, Operation type) {}

  private Result create(Long storeId, String email, AuditActor actor, SurveyCommand.Create c) {
    var survey = surveys.saveAndFlush(SurveySeries.create(storeId, actor.id(), actor.name()));
    var revision =
        revisions.saveAndFlush(
            SurveyRevision.draft(survey, 1, null, c.definition(), actor.id(), actor.name()));
    survey.advance(revision.getId());
    surveys.flush();
    revisionHistory(email, actor, revision, Operation.DRAFT_CREATED, null, null);
    return new Result(revision, null, Operation.DRAFT_CREATED);
  }

  private Result revise(Long storeId, String email, AuditActor actor, SurveyCommand.Revise c) {
    var survey =
        surveys.findByIdAndStoreId(c.surveyId(), storeId).orElseThrow(SurveyService::missing);
    var based = revisionRow(storeId, c.surveyId(), c.basedOn());
    if (!survey.getLatestRevisionId().equals(based.getId())
        || revisions.existsByStoreIdAndSurveyIdAndStatus(
            storeId, c.surveyId(), RevisionStatus.DRAFT))
      throw new ConflictException("最新版を確認してから改版してください");
    var revision =
        revisions.saveAndFlush(
            SurveyRevision.draft(
                survey,
                based.getRevisionNumber() + 1,
                based.getId(),
                c.definition(),
                actor.id(),
                actor.name()));
    survey.advance(revision.getId());
    surveys.flush();
    revisionHistory(email, actor, revision, Operation.DRAFT_CREATED, null, null);
    return new Result(revision, null, Operation.DRAFT_CREATED);
  }

  private Result replace(Long storeId, String email, AuditActor actor, SurveyCommand.Replace c) {
    var row = revisionRow(storeId, c.surveyId(), c.revisionId());
    row.requireVersion(c.version());
    var before = before(row);
    row.replace(c.definition());
    revisions.flush();
    revisionHistory(email, actor, row, Operation.DRAFT_REPLACED, before, null);
    return new Result(row, null, Operation.DRAFT_REPLACED);
  }

  private Result transition(
      Long storeId, String email, AuditActor actor, SurveyCommand.RevisionAction c) {
    var row = revisionRow(storeId, c.surveyId(), c.revisionId());
    row.requireVersion(c.version());
    var before = before(row);
    if (c.type() == Operation.OPENED) {
      if (revisions.existsByStoreIdAndSurveyIdAndStatus(storeId, c.surveyId(), RevisionStatus.OPEN))
        throw new ConflictException("受付中の版を終了してから開始してください");
      row.open(now());
    } else row.close(now());
    revisions.flush();
    revisionHistory(email, actor, row, c.type(), before, c.reason());
    return new Result(row, null, c.type());
  }

  private Result receive(Long storeId, String email, AuditActor actor, SurveyCommand.Receive c) {
    var revision = revisionRow(storeId, c.surveyId(), c.revisionId());
    revision.requireVersion(c.version());
    revision.require(RevisionStatus.OPEN);
    SurveyInput.requirePast(c.input().receivedAt(), now());
    var row =
        answers.saveAndFlush(
            SurveyAnswer.receive(revision, c.input(), actor.id(), actor.name(), null));
    answerHistory(email, actor, row, Operation.RECEIVED, null, null, null);
    return new Result(null, row, Operation.RECEIVED);
  }

  private Result withdraw(Long storeId, String email, AuditActor actor, SurveyCommand.Withdraw c) {
    var row = answerRow(storeId, c.responseId());
    row.requireVersion(c.version());
    var before = before(row);
    row.withdraw();
    answers.flush();
    answerHistory(email, actor, row, Operation.WITHDRAWN, before, c.reason(), null);
    return new Result(null, row, Operation.WITHDRAWN);
  }

  private Result correct(Long storeId, String email, AuditActor actor, SurveyCommand.Correct c) {
    var old = answerRow(storeId, c.responseId());
    old.requireVersion(c.version());
    old.requireNoSuccessor();
    SurveyInput.requirePast(c.input().receivedAt(), now());
    var revision = revisionRow(storeId, old.getSurveyId(), old.getRevisionId());
    var before = before(old);
    var row =
        answers.saveAndFlush(
            SurveyAnswer.receive(revision, c.input(), actor.id(), actor.name(), old.getId()));
    old.correctTo(row.getId());
    answers.flush();
    answerHistory(email, actor, old, Operation.CORRECTION_LINKED, before, c.reason(), row.getId());
    answerHistory(email, actor, row, Operation.CORRECTION_RECEIVED, null, null, old.getId());
    return new Result(null, row, Operation.CORRECTION_RECEIVED);
  }

  private void lock(Long storeId, SurveyCommand command) {
    switch (command) {
      case SurveyCommand.Create _ -> {}
      case SurveyCommand.Revise c ->
          surveys
              .findByIdAndStoreIdForUpdate(c.surveyId(), storeId)
              .orElseThrow(SurveyService::missing);
      case SurveyCommand.Replace c -> lockRevision(storeId, c.surveyId(), c.revisionId(), true);
      case SurveyCommand.RevisionAction c ->
          lockRevision(storeId, c.surveyId(), c.revisionId(), true);
      case SurveyCommand.Receive c -> lockRevision(storeId, c.surveyId(), c.revisionId(), false);
      case SurveyCommand.Withdraw c ->
          answers
              .findByIdAndStoreIdForUpdate(c.responseId(), storeId)
              .orElseThrow(SurveyService::missing);
      case SurveyCommand.Correct c ->
          answers
              .findByIdAndStoreIdForUpdate(c.responseId(), storeId)
              .orElseThrow(SurveyService::missing);
    }
  }

  private void lockRevision(Long storeId, String surveyId, String revisionId, boolean series) {
    if (series)
      surveys.findByIdAndStoreIdForUpdate(surveyId, storeId).orElseThrow(SurveyService::missing);
    var row =
        revisions
            .findByIdAndStoreIdForUpdate(revisionId, storeId)
            .orElseThrow(SurveyService::missing);
    if (!row.getSurveyId().equals(surveyId)) throw missing();
  }

  private SurveyCommand normalize(Long storeId, SurveyCommand command) {
    return switch (command) {
      case SurveyCommand.Receive c ->
          new SurveyCommand.Receive(
              c.surveyId(),
              c.revisionId(),
              c.version(),
              c.input()
                  .forDefinition(revisionRow(storeId, c.surveyId(), c.revisionId()).getQuestions()),
              c.key());
      case SurveyCommand.Correct c -> {
        var row = answerRow(storeId, c.responseId());
        yield new SurveyCommand.Correct(
            c.responseId(),
            c.version(),
            c.reason(),
            c.input()
                .forDefinition(
                    revisionRow(storeId, row.getSurveyId(), row.getRevisionId()).getQuestions()),
            c.key());
      }
      default -> command;
    };
  }

  // 回答は設問キーの順に並べて要約し、並びだけが違う同じ回答を同じ内容として扱う。
  private static String fingerprint(SurveyCommand command) {
    return switch (command) {
      case SurveyCommand.Create c -> Fingerprint.of("CREATE", parts(c.definition()));
      case SurveyCommand.Revise c ->
          Fingerprint.of("REVISE", c.surveyId(), c.basedOn(), parts(c.definition()));
      case SurveyCommand.Replace c ->
          Fingerprint.of(
              "REPLACE", c.surveyId(), c.revisionId(), c.version(), parts(c.definition()));
      case SurveyCommand.RevisionAction c ->
          Fingerprint.of(c.type(), c.surveyId(), c.revisionId(), c.version(), c.reason());
      case SurveyCommand.Receive c ->
          Fingerprint.of("RECEIVE", c.surveyId(), c.revisionId(), c.version(), parts(c.input()));
      case SurveyCommand.Withdraw c ->
          Fingerprint.of("WITHDRAW", c.responseId(), c.version(), c.reason());
      case SurveyCommand.Correct c ->
          Fingerprint.of("CORRECT", c.responseId(), c.version(), c.reason(), parts(c.input()));
    };
  }

  private static List<Object> parts(SurveyDefinition d) {
    return Arrays.asList(
        d.title(),
        d.questions().stream()
            .map(
                q ->
                    Arrays.asList(
                        q.questionKey(),
                        q.type(),
                        q.prompt(),
                        q.required(),
                        q.options().stream()
                            .map(o -> Arrays.asList(o.optionKey(), o.label()))
                            .toList()))
            .toList());
  }

  private static List<Object> parts(SurveyAnswers a) {
    return Arrays.asList(
        a.receivedVia(),
        a.receivedAt(),
        a.answers().stream()
            .sorted(Comparator.comparing(SurveyAnswers.Answer::questionKey))
            .map(x -> Arrays.asList(x.questionKey(), x.text(), x.optionKey()))
            .toList());
  }

  // 受領を持つ設問版・回答は物理削除しないので、対象が無いのは不変条件の破れであって業務拒否ではない。
  private Written respond(Long storeId, Receipt receipt) {
    if (Operation.ofReceipt(receipt.operation()).targetsAnswer())
      return new AnswerWritten(
          answers
              .findByIdAndStoreId(receipt.targetId(), storeId)
              .orElseThrow(SurveyService::broken),
          receipt);
    return new RevisionWritten(
        revisions
            .findByIdAndStoreId(receipt.targetId(), storeId)
            .orElseThrow(SurveyService::broken),
        receipt);
  }

  private SurveyRevision revisionRow(Long storeId, String surveyId, String id) {
    return revisions
        .findByIdAndStoreIdAndSurveyId(id, storeId, surveyId)
        .orElseThrow(SurveyService::missing);
  }

  private SurveyAnswer answerRow(Long storeId, String id) {
    return answers.findByIdAndStoreId(id, storeId).orElseThrow(SurveyService::missing);
  }

  private OffsetDateTime now() {
    return SurveyInput.time(OffsetDateTime.now(clock));
  }

  private static IllegalStateException broken() {
    return new IllegalStateException("受領の対象のアンケート版・回答が見つかりません");
  }

  private static NotFoundException missing() {
    return new NotFoundException("対象のアンケート・設問版・回答が見つかりません");
  }

  private SurveyHistory.Before before(SurveyRevision row) {
    return new SurveyHistory.Before(row.getVersion(), row.getStatus().name());
  }

  private SurveyHistory.Before before(SurveyAnswer row) {
    return new SurveyHistory.Before(row.getVersion(), row.getStatus().name());
  }

  private Map<String, String> snapshot(SurveyHistory.Before before) {
    return before == null
        ? Map.of()
        : Map.of("version", before.version().toString(), "status", before.status());
  }

  private void revisionHistory(
      String email,
      AuditActor actor,
      SurveyRevision row,
      Operation type,
      SurveyHistory.Before before,
      String reason) {
    history.save(SurveyHistory.revision(row, type, actor.id(), actor.name(), before, reason));
    audit.record(
        email,
        row.getStoreId(),
        "SURVEY_" + type,
        "SURVEY_REVISION",
        row.getId(),
        null,
        null,
        snapshot(before),
        snapshot(before(row)));
  }

  private void answerHistory(
      String email,
      AuditActor actor,
      SurveyAnswer row,
      Operation type,
      SurveyHistory.Before before,
      String reason,
      String related) {
    history.save(
        SurveyHistory.answer(row, type, actor.id(), actor.name(), before, reason, related));
    audit.record(
        email,
        row.getStoreId(),
        "SURVEY_" + type,
        "SURVEY_RESPONSE",
        row.getId(),
        null,
        null,
        snapshot(before),
        snapshot(before(row)));
  }
}
