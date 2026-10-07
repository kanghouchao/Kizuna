package com.kizuna.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import com.kizuna.survey.api.dto.SurveyAnswerWriteResponse;
import com.kizuna.survey.api.dto.SurveyRevisionWriteResponse;
import com.kizuna.survey.application.SurveyActors;
import com.kizuna.survey.application.SurveyService;
import com.kizuna.survey.domain.SurveyAnswer;
import com.kizuna.survey.domain.SurveyAnswerDetailView;
import com.kizuna.survey.domain.SurveyAnswerRepository;
import com.kizuna.survey.domain.SurveyAnswers;
import com.kizuna.survey.domain.SurveyCommand;
import com.kizuna.survey.domain.SurveyDefinition;
import com.kizuna.survey.domain.SurveyHistoryRepository;
import com.kizuna.survey.domain.SurveyOperation;
import com.kizuna.survey.domain.SurveyOperationRepository;
import com.kizuna.survey.domain.SurveyRevision;
import com.kizuna.survey.domain.SurveyRevisionDetailView;
import com.kizuna.survey.domain.SurveyRevisionRepository;
import com.kizuna.survey.domain.SurveySeries;
import com.kizuna.survey.domain.SurveySeriesRepository;
import com.kizuna.survey.domain.SurveyValues.AnswerStatus;
import com.kizuna.survey.domain.SurveyValues.Operation;
import com.kizuna.survey.domain.SurveyValues.QuestionType;
import com.kizuna.survey.domain.SurveyValues.ReceivedVia;
import com.kizuna.survey.domain.SurveyValues.RevisionStatus;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class SurveyApplicationTest {
  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-01-01T00:00:00Z");
  private final SurveySeriesRepository surveys = mock(SurveySeriesRepository.class);
  private final SurveyRevisionRepository revisions = mock(SurveyRevisionRepository.class);
  private final SurveyAnswerRepository answers = mock(SurveyAnswerRepository.class);
  private final SurveyOperationRepository operations = mock(SurveyOperationRepository.class);
  private final SurveyHistoryRepository history = mock(SurveyHistoryRepository.class);
  private final SurveyActors actors = mock(SurveyActors.class);
  private final BusinessAudit audit = mock(BusinessAudit.class);
  private final Map<String, SurveySeries> series = new HashMap<>();
  private final Map<String, SurveyRevision> versions = new HashMap<>();
  private final Map<String, SurveyAnswer> responses = new HashMap<>();
  private final Map<String, SurveyOperation> receipts = new HashMap<>();
  private final AtomicLong ids = new AtomicLong(100);
  private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
  private SurveyService service;

  @BeforeEach
  void setup() {
    service =
        new SurveyService(
            surveys,
            revisions,
            answers,
            operations,
            history,
            actors,
            audit,
            Clock.fixed(Instant.parse("2026-10-07T00:00:00Z"), ZoneOffset.UTC));
    when(actors.require(anyString(), any())).thenReturn(new AuditActor(1L, "STAFF", "担当者"));
    when(surveys.saveAndFlush(any())).thenAnswer(call -> store(call.getArgument(0), series));
    when(revisions.saveAndFlush(any())).thenAnswer(call -> store(call.getArgument(0), versions));
    when(answers.saveAndFlush(any())).thenAnswer(call -> store(call.getArgument(0), responses));
    when(operations.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              SurveyOperation row = call.getArgument(0);
              metadata(row);
              receipts.put(row.getDedupeKey(), row);
              return row;
            });
    when(surveys.findById(anyString()))
        .thenAnswer(call -> Optional.ofNullable(series.get(call.getArgument(0))));
    when(surveys.lockById(anyString()))
        .thenAnswer(call -> Optional.ofNullable(series.get(call.getArgument(0))));
    when(revisions.findById(anyString()))
        .thenAnswer(call -> Optional.ofNullable(versions.get(call.getArgument(0))));
    when(revisions.lockById(anyString()))
        .thenAnswer(call -> Optional.ofNullable(versions.get(call.getArgument(0))));
    when(revisions.findByIdAndSurveyId(anyString(), anyString()))
        .thenAnswer(call -> revision(call.getArgument(0), call.getArgument(1)));
    when(revisions.findProjectedByIdAndSurveyId(anyString(), anyString()))
        .thenAnswer(
            call ->
                revision(call.getArgument(0), call.getArgument(1))
                    .map(row -> projections.createProjection(SurveyRevisionDetailView.class, row)));
    when(revisions.existsBySurveyIdAndStatus(anyString(), any()))
        .thenAnswer(
            call ->
                versions.values().stream()
                    .anyMatch(
                        row ->
                            row.getSurveyId().equals(call.getArgument(0))
                                && row.getStatus() == call.getArgument(1)));
    when(answers.findById(anyString()))
        .thenAnswer(call -> Optional.ofNullable(responses.get(call.getArgument(0))));
    when(answers.lockById(anyString()))
        .thenAnswer(call -> Optional.ofNullable(responses.get(call.getArgument(0))));
    when(answers.findProjectedById(anyString()))
        .thenAnswer(
            call ->
                Optional.ofNullable(responses.get(call.getArgument(0)))
                    .map(row -> projections.createProjection(SurveyAnswerDetailView.class, row)));
    when(operations.findByActorIdAndDedupeKey(anyLong(), anyString()))
        .thenAnswer(call -> Optional.ofNullable(receipts.get(call.getArgument(1))));
  }

  private Optional<SurveyRevision> revision(String id, String surveyId) {
    return Optional.ofNullable(versions.get(id)).filter(row -> row.getSurveyId().equals(surveyId));
  }

  private <T extends StoreScopedEntity> T store(T row, Map<String, T> rows) {
    metadata(row);
    rows.put(row.getId(), row);
    return row;
  }

  private void metadata(StoreScopedEntity row) {
    row.setId(Long.toString(ids.incrementAndGet()));
    row.setStoreId(1L);
    row.setCreatedAt(AT);
    row.setUpdatedAt(AT);
    ReflectionTestUtils.setField(row, "version", 0L);
  }

  private SurveyDefinition definition(String title) {
    return new SurveyDefinition(
        title,
        List.of(
            new SurveyDefinition.Question("text", QuestionType.TEXT, "  ご意見  ", true, List.of()),
            new SurveyDefinition.Question(
                "choice",
                QuestionType.SINGLE_CHOICE,
                "選択",
                false,
                List.of(
                    new SurveyDefinition.Option("yes", "はい"),
                    new SurveyDefinition.Option("no", "いいえ")))));
  }

  private SurveyAnswers input(String body) {
    return new SurveyAnswers(
        ReceivedVia.PAPER, AT, List.of(new SurveyAnswers.Answer("text", body, null)));
  }

  private SurveyRevisionWriteResponse create() {
    return (SurveyRevisionWriteResponse)
        service.write("actor", new SurveyCommand.Create(definition("  利用後アンケート  "), "create"));
  }

  private SurveyCommand.RevisionAction action(
      SurveyRevisionWriteResponse result, Operation type, String key) {
    return new SurveyCommand.RevisionAction(
        result.revision().surveyId(), result.revision().id(), 0L, type, "  確認理由  ", key);
  }

  private SurveyCommand.Receive receive(SurveyRevisionWriteResponse result, String key) {
    return new SurveyCommand.Receive(
        result.revision().surveyId(), result.revision().id(), 0L, input("  原文\r\n二行目  "), key);
  }

  @Test
  void versionLifecycleRequiresExplicitCloseAndKeepsPublishedDefinitionImmutable() {
    var first = create();
    var replace =
        new SurveyCommand.Replace(
            first.revision().surveyId(), first.revision().id(), 0L, definition("差替設問"), "replace");
    assertThat(((SurveyRevisionWriteResponse) service.write("actor", replace)).revision().title())
        .isEqualTo("差替設問");
    service.write("actor", action(first, Operation.OPENED, "open"));
    assertThatThrownBy(
            () ->
                service.write(
                    "actor",
                    new SurveyCommand.Replace(
                        first.revision().surveyId(),
                        first.revision().id(),
                        0L,
                        definition("変更禁止"),
                        "replace-again")))
        .isInstanceOf(ConflictException.class);
    var nextCommand =
        new SurveyCommand.Revise(
            first.revision().surveyId(), first.revision().id(), definition("第二版"), "revise");
    var next = (SurveyRevisionWriteResponse) service.write("actor", nextCommand);
    assertThat(next.revision().revisionNumber()).isEqualTo(2);
    assertThat(next.revision().basedOnRevisionId()).isEqualTo(first.revision().id());
    assertThatThrownBy(() -> service.write("actor", action(next, Operation.OPENED, "open-next")))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(
            () ->
                service.write(
                    "actor",
                    new SurveyCommand.Revise(
                        first.revision().surveyId(),
                        first.revision().id(),
                        definition("競合"),
                        "revise-again")))
        .isInstanceOf(ConflictException.class);
    service.write("actor", action(first, Operation.CLOSED, "close"));
    service.write("actor", action(next, Operation.OPENED, "open-next"));
    assertThat(
            service.revision("actor", first.revision().surveyId(), first.revision().id()).title())
        .isEqualTo("差替設問");
    assertThat(
            ((SurveyRevisionWriteResponse) service.replayOnly("actor", nextCommand))
                .revision()
                .status())
        .isEqualTo(RevisionStatus.OPEN);
    assertThatThrownBy(() -> service.write("actor", action(first, Operation.OPENED, "reopen")))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void correctionAfterClosurePreservesOriginalAndReturnsCurrentSuccessorOnReplay() {
    var revision = create();
    service.write("actor", action(revision, Operation.OPENED, "open"));
    var original = (SurveyAnswerWriteResponse) service.write("actor", receive(revision, "receive"));
    service.write("actor", action(revision, Operation.CLOSED, "close"));
    assertThatThrownBy(() -> service.write("actor", receive(revision, "late")))
        .isInstanceOf(ConflictException.class);
    var correction =
        new SurveyCommand.Correct(original.answer().id(), 0L, "訂正理由", input("訂正後"), "correct");
    var successor = (SurveyAnswerWriteResponse) service.write("actor", correction);
    assertThat(successor.answer().revisionId()).isEqualTo(original.answer().revisionId());
    assertThat(successor.answer().supersedesId()).isEqualTo(original.answer().id());
    var previous = service.answer("actor", original.answer().id());
    assertThat(previous.answers().getFirst().text()).isEqualTo("  原文\n二行目  ");
    assertThat(previous.status()).isEqualTo(AnswerStatus.WITHDRAWN);
    assertThat(previous.supersededById()).isEqualTo(successor.answer().id());
    service.write(
        "actor", new SurveyCommand.Withdraw(successor.answer().id(), 0L, "取り下げ理由", "withdraw"));
    var replay = (SurveyAnswerWriteResponse) service.replayOnly("actor", correction);
    assertThat(replay.answer().status()).isEqualTo(AnswerStatus.WITHDRAWN);
    assertThat(replay.operation().id()).isEqualTo(successor.operation().id());
    assertThat(replay.operation().replayed()).isTrue();
    assertThatThrownBy(
            () ->
                service.write(
                    "actor",
                    new SurveyCommand.Correct(
                        original.answer().id(), 0L, "再訂正", input("別回答"), "other-correction")))
        .isInstanceOf(ConflictException.class);
    verify(audit)
        .record(
            eq("actor"),
            eq(1L),
            eq("SURVEY_CORRECTION_LINKED"),
            eq("SURVEY_RESPONSE"),
            eq(original.answer().id()),
            eq(null),
            eq(null),
            eq(Map.of("status", "ACTIVE", "version", "0")),
            eq(Map.of("status", "WITHDRAWN", "version", "0")));
  }

  @Test
  void withdrawnResponseCanHaveOneCorrectionWithoutReactivatingOriginal() {
    var revision = create();
    service.write("actor", action(revision, Operation.OPENED, "open"));
    var first = (SurveyAnswerWriteResponse) service.write("actor", receive(revision, "receive"));
    var withdrawal = new SurveyCommand.Withdraw(first.answer().id(), 0L, "撤回済み", "withdraw");
    service.write("actor", withdrawal);
    assertThatThrownBy(
            () ->
                service.write(
                    "actor", new SurveyCommand.Withdraw(first.answer().id(), 0L, "再撤回", "again")))
        .isInstanceOf(ConflictException.class);
    var corrected =
        (SurveyAnswerWriteResponse)
            service.write(
                "actor",
                new SurveyCommand.Correct(first.answer().id(), 0L, "訂正根拠", input("訂正"), "correct"));
    assertThat(corrected.answer().status()).isEqualTo(AnswerStatus.ACTIVE);
    assertThat(
            ((SurveyAnswerWriteResponse) service.write("actor", withdrawal))
                .answer()
                .supersededById())
        .isEqualTo(corrected.answer().id());
  }

  @Test
  void replayNormalizesAnswerOrderButRejectsChangedMeaningAndRechecksAuthority() {
    var revision = create();
    service.write("actor", action(revision, Operation.OPENED, "open"));
    var text = new SurveyAnswers.Answer("text", "自由回答", null);
    var choice = new SurveyAnswers.Answer("choice", null, "yes");
    var command =
        new SurveyCommand.Receive(
            revision.revision().surveyId(),
            revision.revision().id(),
            0L,
            new SurveyAnswers(ReceivedVia.PAPER, AT, List.of(choice, text)),
            "receive");
    var original = (SurveyAnswerWriteResponse) service.write("actor", command);
    var reordered =
        new SurveyCommand.Receive(
            revision.revision().surveyId(),
            revision.revision().id(),
            0L,
            new SurveyAnswers(ReceivedVia.PAPER, AT, List.of(text, choice)),
            "receive");
    assertThat(service.write("actor", reordered).operation().id())
        .isEqualTo(original.operation().id());
    assertThat(original.answer().answers())
        .extracting("questionKey")
        .containsExactly("text", "choice");
    assertThatThrownBy(() -> service.write("actor", receive(revision, "receive")))
        .isInstanceOf(ConflictException.class);
    doThrow(new AccessDeniedException("権限なし"))
        .when(actors)
        .require("actor", PermissionCode.SURVEY_RECORD);
    assertThatThrownBy(() -> service.replayOnly("actor", reordered))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.write("actor", reordered))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(
            ((SurveyRevisionWriteResponse)
                    service.write(
                        "actor", new SurveyCommand.Create(definition("利用後アンケート"), "create")))
                .revision()
                .status())
        .isEqualTo(RevisionStatus.OPEN);
  }

  @Test
  void staleVersionsMissingResourcesAndFutureReceiptsFailWithoutOperationReceipts() {
    var revision = create();
    assertThatThrownBy(
            () ->
                service.write(
                    "actor",
                    new SurveyCommand.Replace(
                        revision.revision().surveyId(),
                        revision.revision().id(),
                        1L,
                        definition("更新"),
                        "stale")))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> service.revision("actor", "999", revision.revision().id()))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.answer("actor", "999")).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.replayOnly("actor", new SurveyCommand.Create(definition("不存在"), "missing")))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.write(
                    "actor",
                    new SurveyCommand.RevisionAction(
                        "999",
                        revision.revision().id(),
                        0L,
                        Operation.CLOSED,
                        "理由",
                        "missing-series")))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.write(
                    "actor",
                    new SurveyCommand.Receive(
                        "999", revision.revision().id(), 0L, input("原文"), "wrong-series")))
        .isInstanceOf(NotFoundException.class);
    service.write("actor", action(revision, Operation.OPENED, "open"));
    assertThatThrownBy(
            () ->
                service.write(
                    "actor",
                    new SurveyCommand.Receive(
                        revision.revision().surveyId(),
                        revision.revision().id(),
                        0L,
                        new SurveyAnswers(
                            ReceivedVia.PAPER, AT.plusYears(2), input("原文").answers()),
                        "future")))
        .isInstanceOf(ServiceException.class);
    var response = (SurveyAnswerWriteResponse) service.write("actor", receive(revision, "receive"));
    assertThatThrownBy(
            () ->
                service.write(
                    "actor",
                    new SurveyCommand.Withdraw(response.answer().id(), 1L, "理由", "stale-answer")))
        .isInstanceOf(ConflictException.class);
    assertThat(receipts)
        .doesNotContainKeys(
            "stale", "missing", "missing-series", "wrong-series", "future", "stale-answer");
  }

  @Test
  void requiredQuestionsAndOptionMembershipPreventInvalidReceipts() {
    var revision = create();
    service.write("actor", action(revision, Operation.OPENED, "open"));
    for (var values :
        List.of(
            List.of(new SurveyAnswers.Answer("choice", null, "yes")),
            List.of(new SurveyAnswers.Answer("text", null, "yes")),
            List.of(new SurveyAnswers.Answer("unknown", "値", null)),
            List.of(
                new SurveyAnswers.Answer("text", "自由回答", null),
                new SurveyAnswers.Answer("choice", null, "missing")))) {
      var command =
          new SurveyCommand.Receive(
              revision.revision().surveyId(),
              revision.revision().id(),
              0L,
              new SurveyAnswers(ReceivedVia.PAPER, AT, values),
              "invalid");
      assertThatThrownBy(() -> service.write("actor", command))
          .isInstanceOf(ServiceException.class);
    }
    assertThat(responses).isEmpty();
    assertThat(receipts).doesNotContainKey("invalid");
  }
}
