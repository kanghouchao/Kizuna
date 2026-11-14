package com.kizuna.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.receipt.once.Committed;
import com.kizuna.receipt.once.OperationReceipts;
import com.kizuna.receipt.once.Receipt;
import com.kizuna.receipt.once.ReceiptKey;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import com.kizuna.survey.application.SurveyService;
import com.kizuna.survey.application.SurveyViews.AnswerWritten;
import com.kizuna.survey.application.SurveyViews.RevisionWritten;
import com.kizuna.survey.domain.SurveyAnswer;
import com.kizuna.survey.domain.SurveyAnswerDetailView;
import com.kizuna.survey.domain.SurveyAnswerRepository;
import com.kizuna.survey.domain.SurveyAnswers;
import com.kizuna.survey.domain.SurveyCommand;
import com.kizuna.survey.domain.SurveyDefinition;
import com.kizuna.survey.domain.SurveyHistory;
import com.kizuna.survey.domain.SurveyHistoryRepository;
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
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

class SurveyApplicationTest {
  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-01-01T00:00:00Z");
  private static final Long STORE_ID = 7L;
  private final SurveySeriesRepository surveys = mock(SurveySeriesRepository.class);
  private final SurveyRevisionRepository revisions = mock(SurveyRevisionRepository.class);
  private final SurveyAnswerRepository answers = mock(SurveyAnswerRepository.class);
  private final OperationReceipts operations = mock(OperationReceipts.class);
  private final SurveyHistoryRepository history = mock(SurveyHistoryRepository.class);
  private final ActorIdentityService identities = mock(ActorIdentityService.class);
  private final BusinessAudit audit = mock(BusinessAudit.class);
  private final Map<String, SurveySeries> series = new HashMap<>();
  private final Map<String, SurveyRevision> versions = new HashMap<>();
  private final Map<String, SurveyAnswer> responses = new HashMap<>();
  private final Map<String, Committed> receipts = new HashMap<>();
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
            identities,
            audit,
            Clock.fixed(Instant.parse("2026-10-07T00:00:00Z"), ZoneOffset.UTC));
    when(identities.requireActor(anyString())).thenReturn(new AuditActor(1L, "STAFF", "担当者"));
    when(surveys.saveAndFlush(any())).thenAnswer(call -> store(call.getArgument(0), series));
    when(revisions.saveAndFlush(any())).thenAnswer(call -> store(call.getArgument(0), versions));
    when(answers.saveAndFlush(any())).thenAnswer(call -> store(call.getArgument(0), responses));
    // 受領の照会・指紋照合・再送は receipt モジュールの試験が持つ。ここでは常に初回として業務を通す。
    when(operations.once(any(), anyString(), any(), any()))
        .thenAnswer(
            call -> {
              ReceiptKey key = call.getArgument(0);
              assertThat(key.storeId()).isEqualTo(STORE_ID);
              Committed committed = call.<Supplier<Committed>>getArgument(2).get();
              receipts.put(key.operationKey(), committed);
              return call.<Function<Receipt, Object>>getArgument(3)
                  .apply(
                      new Receipt(
                          "r-" + key.operationKey(),
                          committed.operation(),
                          committed.targetId(),
                          committed.facts(),
                          false));
            });
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
    rows.put(row.getId(), row);
    return row;
  }

  private void metadata(StoreScopedEntity row) {
    row.setId("00000000-0000-7000-8000-%012d".formatted(ids.incrementAndGet()));
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

  private RevisionWritten create() {
    return (RevisionWritten)
        service.write(
            STORE_ID, "actor", new SurveyCommand.Create(definition("  利用後アンケート  "), "create"));
  }

  private SurveyCommand.RevisionAction action(RevisionWritten result, Operation type, String key) {
    return new SurveyCommand.RevisionAction(
        result.revision().getSurveyId(), result.revision().getId(), 0L, type, "  確認理由  ", key);
  }

  private SurveyCommand.Receive receive(RevisionWritten result, String key) {
    return new SurveyCommand.Receive(
        result.revision().getSurveyId(),
        result.revision().getId(),
        0L,
        input("  原文\r\n二行目  "),
        key);
  }

  @Test
  void versionLifecycleRequiresExplicitCloseAndKeepsPublishedDefinitionImmutable() {
    var first = create();
    var replace =
        new SurveyCommand.Replace(
            first.revision().getSurveyId(),
            first.revision().getId(),
            0L,
            definition("差替設問"),
            "replace");
    assertThat(((RevisionWritten) service.write(STORE_ID, "actor", replace)).revision().getTitle())
        .isEqualTo("差替設問");
    service.write(STORE_ID, "actor", action(first, Operation.OPENED, "open"));
    assertThatThrownBy(
            () ->
                service.write(
                    STORE_ID,
                    "actor",
                    new SurveyCommand.Replace(
                        first.revision().getSurveyId(),
                        first.revision().getId(),
                        0L,
                        definition("変更禁止"),
                        "replace-again")))
        .isInstanceOf(ConflictException.class);
    var nextCommand =
        new SurveyCommand.Revise(
            first.revision().getSurveyId(), first.revision().getId(), definition("第二版"), "revise");
    var next = (RevisionWritten) service.write(STORE_ID, "actor", nextCommand);
    assertThat(next.revision().getRevisionNumber()).isEqualTo(2);
    assertThat(next.revision().getBasedOnRevisionId()).isEqualTo(first.revision().getId());
    assertThatThrownBy(
            () -> service.write(STORE_ID, "actor", action(next, Operation.OPENED, "open-next")))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(
            () ->
                service.write(
                    STORE_ID,
                    "actor",
                    new SurveyCommand.Revise(
                        first.revision().getSurveyId(),
                        first.revision().getId(),
                        definition("競合"),
                        "revise-again")))
        .isInstanceOf(ConflictException.class);
    service.write(STORE_ID, "actor", action(first, Operation.CLOSED, "close"));
    service.write(STORE_ID, "actor", action(next, Operation.OPENED, "open-next"));
    assertThat(
            service
                .revision(STORE_ID, first.revision().getSurveyId(), first.revision().getId())
                .getTitle())
        .isEqualTo("差替設問");
    assertThatThrownBy(
            () -> service.write(STORE_ID, "actor", action(first, Operation.OPENED, "reopen")))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void correctionAfterClosurePreservesOriginalAndLinksTheSuccessor() {
    var revision = create();
    service.write(STORE_ID, "actor", action(revision, Operation.OPENED, "open"));
    var original = (AnswerWritten) service.write(STORE_ID, "actor", receive(revision, "receive"));
    service.write(STORE_ID, "actor", action(revision, Operation.CLOSED, "close"));
    assertThatThrownBy(() -> service.write(STORE_ID, "actor", receive(revision, "late")))
        .isInstanceOf(ConflictException.class);
    var correction =
        new SurveyCommand.Correct(original.answer().getId(), 0L, "訂正理由", input("訂正後"), "correct");
    var successor = (AnswerWritten) service.write(STORE_ID, "actor", correction);
    assertThat(successor.answer().getRevisionId()).isEqualTo(original.answer().getRevisionId());
    assertThat(successor.answer().getSupersedesId()).isEqualTo(original.answer().getId());
    var previous = service.answer(STORE_ID, original.answer().getId());
    assertThat(previous.getAnswers().getFirst().text()).isEqualTo("  原文\n二行目  ");
    assertThat(previous.getStatus()).isEqualTo(AnswerStatus.WITHDRAWN);
    assertThat(previous.getSupersededById()).isEqualTo(successor.answer().getId());
    assertThatThrownBy(
            () ->
                service.write(
                    STORE_ID,
                    "actor",
                    new SurveyCommand.Correct(
                        original.answer().getId(), 0L, "再訂正", input("別回答"), "other-correction")))
        .isInstanceOf(ConflictException.class);
    verify(audit)
        .record(
            eq("actor"),
            eq(STORE_ID),
            eq("SURVEY_CORRECTION_LINKED"),
            eq("SURVEY_RESPONSE"),
            eq(original.answer().getId()),
            eq(null),
            eq(null),
            eq(Map.of("status", "ACTIVE", "version", "0")),
            eq(Map.of("status", "WITHDRAWN", "version", "0")));
  }

  @Test
  void withdrawnResponseCanHaveOneCorrectionWithoutReactivatingOriginal() {
    var revision = create();
    service.write(STORE_ID, "actor", action(revision, Operation.OPENED, "open"));
    var first = (AnswerWritten) service.write(STORE_ID, "actor", receive(revision, "receive"));
    var withdrawal = new SurveyCommand.Withdraw(first.answer().getId(), 0L, "撤回済み", "withdraw");
    service.write(STORE_ID, "actor", withdrawal);
    assertThatThrownBy(
            () ->
                service.write(
                    STORE_ID,
                    "actor",
                    new SurveyCommand.Withdraw(first.answer().getId(), 0L, "再撤回", "again")))
        .isInstanceOf(ConflictException.class);
    var corrected =
        (AnswerWritten)
            service.write(
                STORE_ID,
                "actor",
                new SurveyCommand.Correct(
                    first.answer().getId(), 0L, "訂正根拠", input("訂正"), "correct"));
    assertThat(corrected.answer().getStatus()).isEqualTo(AnswerStatus.ACTIVE);
    assertThat(service.answer(STORE_ID, first.answer().getId()).getSupersededById())
        .isEqualTo(corrected.answer().getId());
  }

  @Test
  void writesGoThroughTheActorsReceiptAndRecordTheCommittedVersion() {
    var revision = create();
    var opened =
        (RevisionWritten)
            service.write(STORE_ID, "actor", action(revision, Operation.OPENED, "open"));

    assertThat(receipts.get("open"))
        .isEqualTo(
            new Committed(
                "SURVEY_OPENED", revision.revision().getId(), Map.of("committed_version", 0L)));
    assertThat(Operation.ofReceipt(opened.receipt().operation())).isEqualTo(Operation.OPENED);
    assertThat(opened.receipt().targetId()).isEqualTo(revision.revision().getId());
    assertThat(opened.receipt().longFact("committed_version")).isZero();
    assertThat(opened.revision().getStatus()).isEqualTo(RevisionStatus.OPEN);
    var answered = (AnswerWritten) service.write(STORE_ID, "actor", receive(revision, "receive"));
    assertThat(answered.receipt().targetId()).isEqualTo(answered.answer().getId());
    verify(operations).once(eq(new ReceiptKey(STORE_ID, 1L, "receive")), anyString(), any(), any());
  }

  @Test
  void fingerprintIgnoresAnswerOrderButNotAnswerContent() {
    var revision = create();
    var text = new SurveyAnswers.Answer("text", "自由回答", null);
    var choice = new SurveyAnswers.Answer("choice", null, "yes");
    for (var values :
        List.of(
            List.of(choice, text),
            List.of(text, choice),
            List.of(text, new SurveyAnswers.Answer("choice", null, "no")))) {
      service.replayOnly(
          STORE_ID,
          "actor",
          new SurveyCommand.Receive(
              revision.revision().getSurveyId(),
              revision.revision().getId(),
              0L,
              new SurveyAnswers(ReceivedVia.PAPER, AT, values),
              "receive"));
    }

    var fingerprints = ArgumentCaptor.forClass(String.class);
    verify(operations, times(3)).replayOnly(any(), fingerprints.capture(), any());
    var values = fingerprints.getAllValues();
    assertThat(values.get(1)).isEqualTo(values.get(0));
    assertThat(values.get(2)).isNotEqualTo(values.get(0));
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
                        revision.revision().getSurveyId(),
                        revision.revision().getId(),
                        1L,
                        definition("更新"),
                        "stale")))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(
            () ->
                service.revision(
                    STORE_ID, "00000000-0000-7000-8000-000000000999", revision.revision().getId()))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.answer(STORE_ID, "00000000-0000-7000-8000-000000000999"))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                service.write(
                    STORE_ID,
                    "actor",
                    new SurveyCommand.RevisionAction(
                        "00000000-0000-7000-8000-000000000999",
                        revision.revision().getId(),
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
                        "00000000-0000-7000-8000-000000000999",
                        revision.revision().getId(),
                        0L,
                        input("原文"),
                        "wrong-series")))
        .isInstanceOf(NotFoundException.class);
    service.write(STORE_ID, "actor", action(revision, Operation.OPENED, "open"));
    assertThatThrownBy(
            () ->
                service.write(
                    STORE_ID,
                    "actor",
                    new SurveyCommand.Receive(
                        revision.revision().getSurveyId(),
                        revision.revision().getId(),
                        0L,
                        new SurveyAnswers(
                            ReceivedVia.PAPER, AT.plusYears(2), input("原文").answers()),
                        "future")))
        .isInstanceOf(ServiceException.class);
    var response = (AnswerWritten) service.write(STORE_ID, "actor", receive(revision, "receive"));
    assertThatThrownBy(
            () ->
                service.write(
                    STORE_ID,
                    "actor",
                    new SurveyCommand.Withdraw(
                        response.answer().getId(), 1L, "理由", "stale-answer")))
        .isInstanceOf(ConflictException.class);
    assertThat(receipts)
        .doesNotContainKeys("stale", "missing-series", "wrong-series", "future", "stale-answer");
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
              revision.revision().getSurveyId(),
              revision.revision().getId(),
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
