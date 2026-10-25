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
import com.kizuna.survey.domain.SurveyHistory;
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
  private static final Long STORE_ID = 7L;
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
    when(actors.require(eq(STORE_ID), anyString(), any()))
        .thenReturn(new AuditActor(1L, "STAFF", "担当者"));
    when(surveys.saveAndFlush(any())).thenAnswer(call -> store(call.getArgument(0), series));
    when(revisions.saveAndFlush(any())).thenAnswer(call -> store(call.getArgument(0), versions));
    when(answers.saveAndFlush(any())).thenAnswer(call -> store(call.getArgument(0), responses));
    when(operations.saveAndFlush(any())).thenAnswer(call -> store(call.getArgument(0), receipts));
    when(history.save(any()))
        .thenAnswer(
            call -> {
              SurveyHistory row = call.getArgument(0);
              assertThat(row.getStoreId()).isEqualTo(STORE_ID);
              return row;
            });
    when(surveys.findByIdAndStoreId(anyString(), anyLong()))
        .thenAnswer(call -> scoped(series, call.getArgument(0), call.getArgument(1)));
    when(surveys.findByIdAndStoreIdForUpdate(anyString(), anyLong()))
        .thenAnswer(call -> scoped(series, call.getArgument(0), call.getArgument(1)));
    when(revisions.findByIdAndStoreId(anyString(), anyLong()))
        .thenAnswer(call -> scoped(versions, call.getArgument(0), call.getArgument(1)));
    when(revisions.findByIdAndStoreIdForUpdate(anyString(), anyLong()))
        .thenAnswer(call -> scoped(versions, call.getArgument(0), call.getArgument(1)));
    when(revisions.findByIdAndStoreIdAndSurveyId(anyString(), anyLong(), anyString()))
        .thenAnswer(
            call -> revision(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
    when(revisions.findProjectedByIdAndStoreIdAndSurveyId(anyString(), anyLong(), anyString()))
        .thenAnswer(
            call ->
                revision(call.getArgument(0), call.getArgument(1), call.getArgument(2))
                    .map(row -> projections.createProjection(SurveyRevisionDetailView.class, row)));
    when(revisions.existsByStoreIdAndSurveyIdAndStatus(anyLong(), anyString(), any()))
        .thenAnswer(
            call ->
                versions.values().stream()
                    .anyMatch(
                        row ->
                            row.getStoreId().equals(call.getArgument(0))
                                && row.getSurveyId().equals(call.getArgument(1))
                                && row.getStatus() == call.getArgument(2)));
    when(answers.findByIdAndStoreId(anyString(), anyLong()))
        .thenAnswer(call -> scoped(responses, call.getArgument(0), call.getArgument(1)));
    when(answers.findByIdAndStoreIdForUpdate(anyString(), anyLong()))
        .thenAnswer(call -> scoped(responses, call.getArgument(0), call.getArgument(1)));
    when(answers.findProjectedByIdAndStoreId(anyString(), anyLong()))
        .thenAnswer(
            call ->
                scoped(responses, call.getArgument(0), call.getArgument(1))
                    .map(row -> projections.createProjection(SurveyAnswerDetailView.class, row)));
    when(operations.findByStoreIdAndActorIdAndDedupeKey(anyLong(), anyLong(), anyString()))
        .thenAnswer(
            call ->
                Optional.ofNullable(receipts.get(call.getArgument(2)))
                    .filter(row -> row.getStoreId().equals(call.getArgument(0))));
  }

  private <T extends StoreScopedEntity> Optional<T> scoped(
      Map<String, T> rows, String id, Long storeId) {
    return Optional.ofNullable(rows.get(id)).filter(row -> row.getStoreId().equals(storeId));
  }

  private Optional<SurveyRevision> revision(String id, Long storeId, String surveyId) {
    return scoped(versions, id, storeId).filter(row -> row.getSurveyId().equals(surveyId));
  }

  /** 保存される行は、生成した側が店舗を設定していなければならない。 */
  private <T extends StoreScopedEntity> T store(T row, Map<String, T> rows) {
    assertThat(row.getStoreId()).isEqualTo(STORE_ID);
    metadata(row);
    rows.put(
        row instanceof SurveyOperation operation ? operation.getDedupeKey() : row.getId(), row);
    return row;
  }

  private void metadata(StoreScopedEntity row) {
    row.setId(Long.toString(ids.incrementAndGet()));
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
        service.write(
            STORE_ID, "actor", new SurveyCommand.Create(definition("  利用後アンケート  "), "create"));
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
    assertThat(
            ((SurveyRevisionWriteResponse) service.write(STORE_ID, "actor", replace))
                .revision()
                .title())
        .isEqualTo("差替設問");
    service.write(STORE_ID, "actor", action(first, Operation.OPENED, "open"));
    assertThatThrownBy(
            () ->
                service.write(
                    STORE_ID,
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
    var next = (SurveyRevisionWriteResponse) service.write(STORE_ID, "actor", nextCommand);
    assertThat(next.revision().revisionNumber()).isEqualTo(2);
    assertThat(next.revision().basedOnRevisionId()).isEqualTo(first.revision().id());
    assertThatThrownBy(
            () -> service.write(STORE_ID, "actor", action(next, Operation.OPENED, "open-next")))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(
            () ->
                service.write(
                    STORE_ID,
                    "actor",
                    new SurveyCommand.Revise(
                        first.revision().surveyId(),
                        first.revision().id(),
                        definition("競合"),
                        "revise-again")))
        .isInstanceOf(ConflictException.class);
    service.write(STORE_ID, "actor", action(first, Operation.CLOSED, "close"));
    service.write(STORE_ID, "actor", action(next, Operation.OPENED, "open-next"));
    assertThat(
            service
                .revision(STORE_ID, "actor", first.revision().surveyId(), first.revision().id())
                .title())
        .isEqualTo("差替設問");
    assertThat(
            ((SurveyRevisionWriteResponse) service.replayOnly(STORE_ID, "actor", nextCommand))
                .revision()
                .status())
        .isEqualTo(RevisionStatus.OPEN);
    assertThatThrownBy(
            () -> service.write(STORE_ID, "actor", action(first, Operation.OPENED, "reopen")))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void correctionAfterClosurePreservesOriginalAndReturnsCurrentSuccessorOnReplay() {
    var revision = create();
    service.write(STORE_ID, "actor", action(revision, Operation.OPENED, "open"));
    var original =
        (SurveyAnswerWriteResponse) service.write(STORE_ID, "actor", receive(revision, "receive"));
    service.write(STORE_ID, "actor", action(revision, Operation.CLOSED, "close"));
    assertThatThrownBy(() -> service.write(STORE_ID, "actor", receive(revision, "late")))
        .isInstanceOf(ConflictException.class);
    var correction =
        new SurveyCommand.Correct(original.answer().id(), 0L, "訂正理由", input("訂正後"), "correct");
    var successor = (SurveyAnswerWriteResponse) service.write(STORE_ID, "actor", correction);
    assertThat(successor.answer().revisionId()).isEqualTo(original.answer().revisionId());
    assertThat(successor.answer().supersedesId()).isEqualTo(original.answer().id());
    var previous = service.answer(STORE_ID, "actor", original.answer().id());
    assertThat(previous.answers().getFirst().text()).isEqualTo("  原文\n二行目  ");
    assertThat(previous.status()).isEqualTo(AnswerStatus.WITHDRAWN);
    assertThat(previous.supersededById()).isEqualTo(successor.answer().id());
    service.write(
        STORE_ID,
        "actor",
        new SurveyCommand.Withdraw(successor.answer().id(), 0L, "取り下げ理由", "withdraw"));
    var replay = (SurveyAnswerWriteResponse) service.replayOnly(STORE_ID, "actor", correction);
    assertThat(replay.answer().status()).isEqualTo(AnswerStatus.WITHDRAWN);
    assertThat(replay.operation().id()).isEqualTo(successor.operation().id());
    assertThat(replay.operation().replayed()).isTrue();
    assertThatThrownBy(
            () ->
                service.write(
                    STORE_ID,
                    "actor",
                    new SurveyCommand.Correct(
                        original.answer().id(), 0L, "再訂正", input("別回答"), "other-correction")))
        .isInstanceOf(ConflictException.class);
    verify(audit)
        .record(
            eq("actor"),
            eq(STORE_ID),
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
    service.write(STORE_ID, "actor", action(revision, Operation.OPENED, "open"));
    var first =
        (SurveyAnswerWriteResponse) service.write(STORE_ID, "actor", receive(revision, "receive"));
    var withdrawal = new SurveyCommand.Withdraw(first.answer().id(), 0L, "撤回済み", "withdraw");
    service.write(STORE_ID, "actor", withdrawal);
    assertThatThrownBy(
            () ->
                service.write(
                    STORE_ID,
                    "actor",
                    new SurveyCommand.Withdraw(first.answer().id(), 0L, "再撤回", "again")))
        .isInstanceOf(ConflictException.class);
    var corrected =
        (SurveyAnswerWriteResponse)
            service.write(
                STORE_ID,
                "actor",
                new SurveyCommand.Correct(first.answer().id(), 0L, "訂正根拠", input("訂正"), "correct"));
    assertThat(corrected.answer().status()).isEqualTo(AnswerStatus.ACTIVE);
    assertThat(
            ((SurveyAnswerWriteResponse) service.write(STORE_ID, "actor", withdrawal))
                .answer()
                .supersededById())
        .isEqualTo(corrected.answer().id());
  }

  @Test
  void replayNormalizesAnswerOrderButRejectsChangedMeaningAndRechecksAuthority() {
    var revision = create();
    service.write(STORE_ID, "actor", action(revision, Operation.OPENED, "open"));
    var text = new SurveyAnswers.Answer("text", "自由回答", null);
    var choice = new SurveyAnswers.Answer("choice", null, "yes");
    var command =
        new SurveyCommand.Receive(
            revision.revision().surveyId(),
            revision.revision().id(),
            0L,
            new SurveyAnswers(ReceivedVia.PAPER, AT, List.of(choice, text)),
            "receive");
    var original = (SurveyAnswerWriteResponse) service.write(STORE_ID, "actor", command);
    var reordered =
        new SurveyCommand.Receive(
            revision.revision().surveyId(),
            revision.revision().id(),
            0L,
            new SurveyAnswers(ReceivedVia.PAPER, AT, List.of(text, choice)),
            "receive");
    assertThat(service.write(STORE_ID, "actor", reordered).operation().id())
        .isEqualTo(original.operation().id());
    assertThat(original.answer().answers())
        .extracting("questionKey")
        .containsExactly("text", "choice");
    assertThatThrownBy(() -> service.write(STORE_ID, "actor", receive(revision, "receive")))
        .isInstanceOf(ConflictException.class);
    doThrow(new AccessDeniedException("権限なし"))
        .when(actors)
        .require(STORE_ID, "actor", PermissionCode.SURVEY_RECORD);
    assertThatThrownBy(() -> service.replayOnly(STORE_ID, "actor", reordered))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.write(STORE_ID, "actor", reordered))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(
            ((SurveyRevisionWriteResponse)
                    service.write(
                        STORE_ID,
                        "actor",
                        new SurveyCommand.Create(definition("利用後アンケート"), "create")))
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
                    STORE_ID,
                    "actor",
                    new SurveyCommand.Replace(
                        revision.revision().surveyId(),
                        revision.revision().id(),
                        1L,
                        definition("更新"),
                        "stale")))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> service.revision(STORE_ID, "actor", "999", revision.revision().id()))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.answer(STORE_ID, "actor", "999"))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.replayOnly(
                    STORE_ID, "actor", new SurveyCommand.Create(definition("不存在"), "missing")))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.write(
                    STORE_ID,
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
                    STORE_ID,
                    "actor",
                    new SurveyCommand.Receive(
                        "999", revision.revision().id(), 0L, input("原文"), "wrong-series")))
        .isInstanceOf(NotFoundException.class);
    service.write(STORE_ID, "actor", action(revision, Operation.OPENED, "open"));
    assertThatThrownBy(
            () ->
                service.write(
                    STORE_ID,
                    "actor",
                    new SurveyCommand.Receive(
                        revision.revision().surveyId(),
                        revision.revision().id(),
                        0L,
                        new SurveyAnswers(
                            ReceivedVia.PAPER, AT.plusYears(2), input("原文").answers()),
                        "future")))
        .isInstanceOf(ServiceException.class);
    var response =
        (SurveyAnswerWriteResponse) service.write(STORE_ID, "actor", receive(revision, "receive"));
    assertThatThrownBy(
            () ->
                service.write(
                    STORE_ID,
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
    service.write(STORE_ID, "actor", action(revision, Operation.OPENED, "open"));
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
      assertThatThrownBy(() -> service.write(STORE_ID, "actor", command))
          .isInstanceOf(ServiceException.class);
    }
    assertThat(responses).isEmpty();
    assertThat(receipts).doesNotContainKey("invalid");
  }
}
