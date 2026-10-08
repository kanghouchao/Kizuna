package com.kizuna.survey.application;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.survey.api.dto.SurveyAnswerResponse;
import com.kizuna.survey.api.dto.SurveyAnswerWriteResponse;
import com.kizuna.survey.api.dto.SurveyOperationResponse;
import com.kizuna.survey.api.dto.SurveyResponseCountsResponse;
import com.kizuna.survey.api.dto.SurveyRevisionResponse;
import com.kizuna.survey.api.dto.SurveyRevisionWriteResponse;
import com.kizuna.survey.api.dto.SurveyWriteResponse;
import com.kizuna.survey.domain.SurveyAnswer;
import com.kizuna.survey.domain.SurveyAnswerRepository;
import com.kizuna.survey.domain.SurveyCommand;
import com.kizuna.survey.domain.SurveyFingerprint;
import com.kizuna.survey.domain.SurveyHistory;
import com.kizuna.survey.domain.SurveyHistoryRepository;
import com.kizuna.survey.domain.SurveyInput;
import com.kizuna.survey.domain.SurveyOperation;
import com.kizuna.survey.domain.SurveyOperationRepository;
import com.kizuna.survey.domain.SurveyRevision;
import com.kizuna.survey.domain.SurveyRevisionRepository;
import com.kizuna.survey.domain.SurveySeries;
import com.kizuna.survey.domain.SurveySeriesRepository;
import com.kizuna.survey.domain.SurveyValues.Operation;
import com.kizuna.survey.domain.SurveyValues.RevisionStatus;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.OffsetDateTime;
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
  private final SurveyOperationRepository operations;
  private final SurveyHistoryRepository history;
  private final SurveyActors actors;
  private final BusinessAudit audit;
  private final Clock clock;

  @StoreScoped
  @Transactional
  public SurveyWriteResponse write(String email, SurveyCommand command) {
    var actor = actors.require(email, permission(command));
    lock(command);
    command = normalize(command);
    var hash = fingerprint(command);
    var old = operations.findByActorIdAndDedupeKey(actor.id(), command.key());
    if (old.isPresent()) return replay(old.get(), hash);
    var result =
        switch (command) {
          case SurveyCommand.Create c -> create(email, actor, c);
          case SurveyCommand.Revise c -> revise(email, actor, c);
          case SurveyCommand.Replace c -> replace(email, actor, c);
          case SurveyCommand.RevisionAction c -> transition(email, actor, c);
          case SurveyCommand.Receive c -> receive(email, actor, c);
          case SurveyCommand.Withdraw c -> withdraw(email, actor, c);
          case SurveyCommand.Correct c -> correct(email, actor, c);
        };
    var receipt =
        operations.saveAndFlush(
            SurveyOperation.record(
                actor.id(),
                command.key(),
                hash,
                result.type(),
                result.revision() == null ? result.answer() : result.revision(),
                result.answer() != null));
    return result.revision() == null
        ? new SurveyAnswerWriteResponse(
            SurveyAnswerResponse.of(result.answer()), SurveyOperationResponse.of(receipt, false))
        : new SurveyRevisionWriteResponse(
            SurveyRevisionResponse.of(result.revision()),
            SurveyOperationResponse.of(receipt, false));
  }

  @StoreScoped
  @Transactional
  public SurveyWriteResponse replayOnly(String email, SurveyCommand command) {
    var actor = actors.require(email, permission(command));
    lock(command);
    command = normalize(command);
    var receipt =
        operations
            .findByActorIdAndDedupeKey(actor.id(), command.key())
            .orElseThrow(SurveyService::missing);
    return replay(receipt, fingerprint(command));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public SurveyRevisionResponse revision(String email, String surveyId, String id) {
    actors.require(email, PermissionCode.SURVEY_VIEW);
    return SurveyRevisionResponse.of(
        revisions
            .findProjectedByIdAndSurveyId(SurveyInput.id(id), SurveyInput.id(surveyId))
            .orElseThrow(SurveyService::missing));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public SurveyAnswerResponse answer(String email, String id) {
    actors.require(email, PermissionCode.SURVEY_VIEW);
    return SurveyAnswerResponse.of(
        answers.findProjectedById(SurveyInput.id(id)).orElseThrow(SurveyService::missing));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public SurveyResponseCountsResponse counts(String email, String surveyId, String id) {
    actors.require(email, PermissionCode.SURVEY_VIEW);
    if (!revisions.existsByIdAndSurveyId(SurveyInput.id(id), SurveyInput.id(surveyId)))
      throw missing();
    var count = answers.counts(id);
    return new SurveyResponseCountsResponse(
        surveyId,
        id,
        count.getTotalRecords(),
        count.getActiveRecords(),
        count.getWithdrawnRecords());
  }

  private record Result(SurveyRevision revision, SurveyAnswer answer, Operation type) {}

  private Result create(String email, AuditActor actor, SurveyCommand.Create c) {
    var survey = surveys.saveAndFlush(SurveySeries.create(actor.id(), actor.name()));
    var revision =
        revisions.saveAndFlush(
            SurveyRevision.draft(
                survey.getId(), 1, null, c.definition(), actor.id(), actor.name()));
    survey.advance(revision.getId());
    surveys.flush();
    revisionHistory(email, actor, revision, Operation.DRAFT_CREATED, null, null);
    return new Result(revision, null, Operation.DRAFT_CREATED);
  }

  private Result revise(String email, AuditActor actor, SurveyCommand.Revise c) {
    var survey = surveys.findById(c.surveyId()).orElseThrow(SurveyService::missing);
    var based = revisionRow(c.surveyId(), c.basedOn());
    if (!survey.getLatestRevisionId().equals(based.getId())
        || revisions.existsBySurveyIdAndStatus(c.surveyId(), RevisionStatus.DRAFT))
      throw new ConflictException("最新版を確認してから改版してください");
    var revision =
        revisions.saveAndFlush(
            SurveyRevision.draft(
                survey.getId(),
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

  private Result replace(String email, AuditActor actor, SurveyCommand.Replace c) {
    var row = revisionRow(c.surveyId(), c.revisionId());
    row.requireVersion(c.version());
    var before = before(row);
    row.replace(c.definition());
    revisions.flush();
    revisionHistory(email, actor, row, Operation.DRAFT_REPLACED, before, null);
    return new Result(row, null, Operation.DRAFT_REPLACED);
  }

  private Result transition(String email, AuditActor actor, SurveyCommand.RevisionAction c) {
    var row = revisionRow(c.surveyId(), c.revisionId());
    row.requireVersion(c.version());
    var before = before(row);
    if (c.type() == Operation.OPENED) {
      if (revisions.existsBySurveyIdAndStatus(c.surveyId(), RevisionStatus.OPEN))
        throw new ConflictException("受付中の版を終了してから開始してください");
      row.open(now());
    } else row.close(now());
    revisions.flush();
    revisionHistory(email, actor, row, c.type(), before, c.reason());
    return new Result(row, null, c.type());
  }

  private Result receive(String email, AuditActor actor, SurveyCommand.Receive c) {
    var revision = revisionRow(c.surveyId(), c.revisionId());
    revision.requireVersion(c.version());
    revision.require(RevisionStatus.OPEN);
    SurveyInput.requirePast(c.input().receivedAt(), now());
    var row =
        answers.saveAndFlush(
            SurveyAnswer.receive(revision, c.input(), actor.id(), actor.name(), null));
    answerHistory(email, actor, row, Operation.RECEIVED, null, null, null);
    return new Result(null, row, Operation.RECEIVED);
  }

  private Result withdraw(String email, AuditActor actor, SurveyCommand.Withdraw c) {
    var row = answerRow(c.responseId());
    row.requireVersion(c.version());
    var before = before(row);
    row.withdraw();
    answers.flush();
    answerHistory(email, actor, row, Operation.WITHDRAWN, before, c.reason(), null);
    return new Result(null, row, Operation.WITHDRAWN);
  }

  private Result correct(String email, AuditActor actor, SurveyCommand.Correct c) {
    var old = answerRow(c.responseId());
    old.requireVersion(c.version());
    old.requireNoSuccessor();
    SurveyInput.requirePast(c.input().receivedAt(), now());
    var revision = revisionRow(old.getSurveyId(), old.getRevisionId());
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

  private void lock(SurveyCommand command) {
    switch (command) {
      case SurveyCommand.Create ignored -> {}
      case SurveyCommand.Revise c ->
          surveys.lockById(c.surveyId()).orElseThrow(SurveyService::missing);
      case SurveyCommand.Replace c -> lockRevision(c.surveyId(), c.revisionId(), true);
      case SurveyCommand.RevisionAction c -> lockRevision(c.surveyId(), c.revisionId(), true);
      case SurveyCommand.Receive c -> lockRevision(c.surveyId(), c.revisionId(), false);
      case SurveyCommand.Withdraw c ->
          answers.lockById(c.responseId()).orElseThrow(SurveyService::missing);
      case SurveyCommand.Correct c ->
          answers.lockById(c.responseId()).orElseThrow(SurveyService::missing);
    }
  }

  private void lockRevision(String surveyId, String revisionId, boolean series) {
    if (series) surveys.lockById(surveyId).orElseThrow(SurveyService::missing);
    var row = revisions.lockById(revisionId).orElseThrow(SurveyService::missing);
    if (!row.getSurveyId().equals(surveyId)) throw missing();
  }

  private SurveyCommand normalize(SurveyCommand command) {
    return switch (command) {
      case SurveyCommand.Receive c ->
          new SurveyCommand.Receive(
              c.surveyId(),
              c.revisionId(),
              c.version(),
              c.input().forDefinition(revisionRow(c.surveyId(), c.revisionId()).getQuestions()),
              c.key());
      case SurveyCommand.Correct c -> {
        var row = answerRow(c.responseId());
        yield new SurveyCommand.Correct(
            c.responseId(),
            c.version(),
            c.reason(),
            c.input()
                .forDefinition(revisionRow(row.getSurveyId(), row.getRevisionId()).getQuestions()),
            c.key());
      }
      default -> command;
    };
  }

  private String fingerprint(SurveyCommand command) {
    return switch (command) {
      case SurveyCommand.Create c -> SurveyFingerprint.of("CREATE", c.definition());
      case SurveyCommand.Revise c ->
          SurveyFingerprint.of("REVISE", c.surveyId(), c.basedOn(), c.definition());
      case SurveyCommand.Replace c ->
          SurveyFingerprint.of(
              "REPLACE", c.surveyId(), c.revisionId(), c.version(), c.definition());
      case SurveyCommand.RevisionAction c ->
          SurveyFingerprint.of(c.type(), c.surveyId(), c.revisionId(), c.version(), c.reason());
      case SurveyCommand.Receive c ->
          SurveyFingerprint.of("RECEIVE", c.surveyId(), c.revisionId(), c.version(), c.input());
      case SurveyCommand.Withdraw c ->
          SurveyFingerprint.of("WITHDRAW", c.responseId(), c.version(), c.reason());
      case SurveyCommand.Correct c ->
          SurveyFingerprint.of("CORRECT", c.responseId(), c.version(), c.reason(), c.input());
    };
  }

  private PermissionCode permission(SurveyCommand command) {
    return command instanceof SurveyCommand.Receive
            || command instanceof SurveyCommand.Withdraw
            || command instanceof SurveyCommand.Correct
        ? PermissionCode.SURVEY_RECORD
        : PermissionCode.SURVEY_MANAGE;
  }

  private SurveyWriteResponse replay(SurveyOperation operation, String hash) {
    operation.requireSame(hash);
    var receipt = SurveyOperationResponse.of(operation, true);
    if (operation.getResponseId() != null)
      return new SurveyAnswerWriteResponse(
          SurveyAnswerResponse.of(answerRow(operation.getResponseId())), receipt);
    return new SurveyRevisionWriteResponse(
        SurveyRevisionResponse.of(
            revisions.findById(operation.getRevisionId()).orElseThrow(SurveyService::missing)),
        receipt);
  }

  private SurveyRevision revisionRow(String surveyId, String id) {
    return revisions.findByIdAndSurveyId(id, surveyId).orElseThrow(SurveyService::missing);
  }

  private SurveyAnswer answerRow(String id) {
    return answers.findById(id).orElseThrow(SurveyService::missing);
  }

  private OffsetDateTime now() {
    return SurveyInput.time(OffsetDateTime.now(clock));
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
